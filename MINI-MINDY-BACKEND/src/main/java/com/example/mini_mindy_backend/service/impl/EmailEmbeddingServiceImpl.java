package com.example.mini_mindy_backend.service.impl;


import com.example.mini_mindy_backend.model.EmailEmbedding;
import com.example.mini_mindy_backend.model.User;
import com.example.mini_mindy_backend.repository.EmailEmbeddingRepository;
import com.example.mini_mindy_backend.repository.UserRepository;
import com.example.mini_mindy_backend.service.EmailEmbeddingService;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailEmbeddingServiceImpl implements EmailEmbeddingService {

    private final EmailEmbeddingRepository emailRepository;
    private final UserRepository userRepository;
    private final EmbeddingModel embeddingModel;

    // MinIO config (port 9000 = S3 API, 9001 = Web Console)
    private final String MINIO_URL = "http://localhost:9000";
    private final String MINIO_ACCESS = "minioadmin";
    private final String MINIO_SECRET = "minioadmin123";
    private final String BUCKET = "datalake";


    @Override
    public void processEmailsFromMinIO(String userId) {
        try {
            // Get user email from userId (UUID)
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            String userEmail = user.getEmail();
            System.out.println("[INFO] Processing embeddings for user: " + userEmail + " (ID: " + userId + ")");
            
            // MinIO connection
            MinioClient minio = MinioClient.builder()
                    .endpoint(MINIO_URL)
                    .credentials(MINIO_ACCESS, MINIO_SECRET)
                    .build();

            List<String> parquetFiles = new ArrayList<>();

            // 1. Retrieve ALL parquet files for this user (from ALL dates)
            try {
                // Path format: bronze/emails/user_email=xxx/date=yyy/
                String userPrefix = "bronze/emails/user_email=" + userEmail + "/";
                
                var results = minio.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(BUCKET)
                                .prefix(userPrefix)
                                .recursive(true)
                                .build()
                );

                // Collect ALL parquet files for this user (TOUTES les dates)
                for (var result : results) {
                    try {
                        String objectName = result.get().objectName();
                        // Ajouter TOUS les fichiers .parquet (pas seulement la dernière date)
                        if (objectName.endsWith(".parquet")) {
                            parquetFiles.add(objectName);
                            System.out.println("[INFO] Found parquet file: " + objectName);
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Error reading object: " + e.getMessage());
                    }
                }
                
                System.out.println("[INFO] Total parquet files found for user " + userEmail + ": " + parquetFiles.size());
                    
                } catch (Exception e) {
                    System.err.println("[ERROR] MinIO listing error for user " + userEmail + ": " + e.getMessage());
                    throw new RuntimeException("Unable to list MinIO objects for user: " + userEmail, e);
                }

                System.out.println("[INFO] Parquet files to process for user " + userEmail + ": " + parquetFiles.size());

            // 2. Read files
            for (String file : parquetFiles) {
                try {
                    System.out.println("[INFO] Reading file: " + file);

                    InputStream stream = minio.getObject(GetObjectArgs.builder()
                            .bucket(BUCKET)
                            .object(file)
                            .build());

                    // Utiliser un nom de fichier temporaire unique pour chaque fichier
                    String tempPath = "temp_" + System.currentTimeMillis() + "_" + file.hashCode() + ".parquet";
                    Files.copy(stream, Paths.get(tempPath), StandardCopyOption.REPLACE_EXISTING);

                    // Hadoop configuration to read INT96 (Spark timestamps)
                    org.apache.hadoop.conf.Configuration hadoopConf = new org.apache.hadoop.conf.Configuration();
                    hadoopConf.setBoolean("parquet.avro.readInt96AsFixed", true);

                    ParquetReader<GenericRecord> reader =
                            AvroParquetReader.<GenericRecord>builder(
                                    HadoopInputFile.fromPath(
                                            new org.apache.hadoop.fs.Path(tempPath),
                                            hadoopConf
                                    )
                            ).withConf(hadoopConf).build();

                    // 3. Process emails
                    GenericRecord record;
                    while ((record = reader.read()) != null) {
                        String id = Objects.toString(record.get("id"), null);
                        if (id == null) continue;

                        if (emailRepository.existsById(id)) {
                            System.out.println("[WARN] Already exists: " + id);
                            continue;
                        }

                        String subject = Objects.toString(record.get("subject"), "");
                        String body = Objects.toString(record.get("body"), "");

                        // Truncate text to respect OpenAI token limit (8192 tokens)
                        // Conservative estimate: ~2 characters per token for safety
                        // 8000 tokens * 2 chars = 16000 chars max, using 10000 for extra margin
                        final int MAX_CHARS = 10000;
                        if (subject.length() > MAX_CHARS) {
                            subject = subject.substring(0, MAX_CHARS);
                            System.out.println("[INFO] Truncated subject from " + Objects.toString(record.get("subject"), "").length() + " to " + MAX_CHARS + " chars");
                        }
                        if (body.length() > MAX_CHARS) {
                            System.out.println("[INFO] Truncated body from " + body.length() + " to " + MAX_CHARS + " chars");
                            body = body.substring(0, MAX_CHARS);
                        }

                        // 4. Generate embeddings using Spring AI
                        String subjectEmbStr = generateEmbedding(subject);
                        String bodyEmbStr = generateEmbedding(body);

                        // 5. Save with native SQL query (including original text for RAG)
                        String sender = extractEmail(Objects.toString(record.get("from"), ""));
                        String receiver = extractEmail(Objects.toString(record.get("to"), ""));
                        String senderDomain = Objects.toString(record.get("sender_domain"), "");
                        boolean isImportant = Boolean.parseBoolean(
                                Objects.toString(record.get("is_important"), "false")
                        );
                        
                        // Extract email_timestamp and convert to OffsetDateTime
                        // Support tous les formats Parquet: INT96, Long, String, byte[], List<Integer>
                        java.time.OffsetDateTime emailDate = null;
                        try {
                            Object timestampObj = record.get("email_timestamp");
                            if (timestampObj != null) {
                                if (timestampObj instanceof Long) {
                                    // Timestamp en microsecondes depuis epoch
                                    long micros = (Long) timestampObj;
                                    emailDate = java.time.OffsetDateTime.ofInstant(
                                        java.time.Instant.ofEpochMilli(micros / 1000),
                                        java.time.ZoneOffset.UTC
                                    );
                                } else if (timestampObj instanceof CharSequence) {
                                    // String ISO 8601
                                    String timestampStr = timestampObj.toString();
                                    emailDate = java.time.OffsetDateTime.parse(timestampStr);
                                } else if (timestampObj instanceof org.apache.avro.generic.GenericData.Fixed) {
                                    // INT96 Parquet (12 bytes)
                                    byte[] bytes = ((org.apache.avro.generic.GenericData.Fixed) timestampObj).bytes();
                                    emailDate = convertInt96ToOffsetDateTime(bytes);
                                } else if (timestampObj instanceof byte[]) {
                                    // INT96 Parquet (12 bytes)
                                    emailDate = convertInt96ToOffsetDateTime((byte[]) timestampObj);
                                } else if (timestampObj instanceof java.util.List) {
                                    // Parfois Avro retourne List<Integer> pour INT96
                                    java.util.List<?> list = (java.util.List<?>) timestampObj;
                                    if (list.size() == 12) {
                                        byte[] bytes = new byte[12];
                                        for (int i = 0; i < 12; i++) {
                                            bytes[i] = ((Number) list.get(i)).byteValue();
                                        }
                                        emailDate = convertInt96ToOffsetDateTime(bytes);
                                    } else {
                                        System.err.println("[WARN] Unexpected List size for email_timestamp: " + list.size());
                                        emailDate = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
                                    }
                                } else {
                                    System.err.println("[WARN] Unknown email_timestamp type: " + timestampObj.getClass().getName());
                                    emailDate = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("[WARN] Unable to parse email_timestamp for " + id + ": " + e.getMessage());
                            emailDate = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
                        }

                        // Insert with userId for multi-user isolation
                        emailRepository.insertWithEmbeddingsAndUserId(
                                id, userId, sender, receiver, senderDomain, isImportant,
                                subject, body, emailDate,
                                subjectEmbStr, bodyEmbStr
                        );

                    }

                    reader.close();
                    
                    // Nettoyer le fichier temporaire
                    try {
                        Files.deleteIfExists(Paths.get(tempPath));
                        System.out.println("[INFO] Cleaned up temp file: " + tempPath);
                    } catch (Exception cleanupEx) {
                        System.err.println("[WARN] Could not delete temp file " + tempPath + ": " + cleanupEx.getMessage());
                    }

                } catch (Exception e) {
                    System.err.println("[ERROR] Processing file " + file + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }


        } catch (Exception e) {
            System.err.println("[FATAL] " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error processing emails from MinIO", e);
        }
    }

    /** Generate embedding using Spring AI EmbeddingModel */
    private String generateEmbedding(String text) {
        if (text == null || text.isEmpty()) {
            text = " "; // Avoid empty input
        }
        float[] embedding = embeddingModel.embed(text);
        
        // Convert to pgvector format [x,y,z,...]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Extrait uniquement l'adresse email depuis un format 'Nom <email>' ou 'email'
     * Exemples:
     *   "Alae Haddad <alaehaddad205@gmail.com>" -> "alaehaddad205@gmail.com"
     *   "alaehaddad205@gmail.com" -> "alaehaddad205@gmail.com"
     */
    private String extractEmail(String emailField) {
        if (emailField == null || emailField.isEmpty()) {
            return "";
        }
        
        // Si le format est "Nom <email>"
        int startBracket = emailField.indexOf('<');
        int endBracket = emailField.indexOf('>');
        
        if (startBracket != -1 && endBracket != -1 && endBracket > startBracket) {
            return emailField.substring(startBracket + 1, endBracket).trim();
        }
        
        // Sinon retourner tel quel (déjà un email simple)
        return emailField.trim();
    }

    /**
     * Convertit un champ Parquet INT96 (12 bytes) en OffsetDateTime UTC
     * Format INT96: [0-7] = nanoseconds, [8-11] = Julian day (little endian)
     */
    private java.time.OffsetDateTime convertInt96ToOffsetDateTime(byte[] bytes) {
        if (bytes.length != 12) {
            throw new IllegalArgumentException("INT96 timestamp must be 12 bytes, got " + bytes.length);
        }
        
        // Lire en little endian
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        long nanos = buffer.getLong();  // 8 premiers bytes = nanoseconds
        int julianDay = buffer.getInt(); // 4 derniers bytes = Julian day
        
        // Convertir Julian day en epoch millis
        // Julian day 2440588 = 1970-01-01 (epoch Unix)
        long epochDay = julianDay - 2440588L;
        long epochMillis = epochDay * 86400000L + (nanos / 1000000L);
        
        return java.time.OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMillis),
            java.time.ZoneOffset.UTC
        );
    }


    @Override
    public List<EmailEmbedding> getAllEmbeddings() {
        return emailRepository.findAll();
    }
}