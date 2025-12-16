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

            // 1. Retrieve parquet files ONLY for this user (from the LATEST date)
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

                // First pass: find the latest date folder
                String latestDate = null;
                for (var result : results) {
                    try {
                        String objectName = result.get().objectName();
                        // Extract date from path: bronze/emails/user_email=xxx/date=2025-12-16/xxx.parquet
                        if (objectName.contains("/date=")) {
                            int dateStart = objectName.indexOf("/date=") + 6;
                            int dateEnd = objectName.indexOf("/", dateStart);
                            if (dateEnd > dateStart) {
                                String date = objectName.substring(dateStart, dateEnd);
                                if (latestDate == null || date.compareTo(latestDate) > 0) {
                                    latestDate = date;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Error parsing date from object: " + e.getMessage());
                    }
                }

                System.out.println("[INFO] Latest date found for user " + userEmail + ": " + latestDate);

                    // Second pass: retrieve only parquet files from the latest date (sorted)
                    results = minio.listObjects(
                            ListObjectsArgs.builder()
                                    .bucket(BUCKET)
                                    .prefix(userPrefix)
                                    .recursive(true)
                                    .build()
                    );

                    List<String> allParquetFiles = new ArrayList<>();
                    for (var result : results) {
                        try {
                            String objectName = result.get().objectName();
                            // Filter only .parquet files from the latest date
                            if (objectName.endsWith(".parquet") && objectName.contains("/date=" + latestDate + "/")) {
                                allParquetFiles.add(objectName);
                            }
                        } catch (Exception e) {
                            System.err.println("[ERROR] Error reading object: " + e.getMessage());
                        }
                    }

                    // Sort files by Last Modified time (from MinIO stat)
                    allParquetFiles.sort((file1, file2) -> {
                        try {
                            var stat1 = minio.statObject(
                                    io.minio.StatObjectArgs.builder()
                                            .bucket(BUCKET)
                                            .object(file1)
                                            .build()
                            );
                            var stat2 = minio.statObject(
                                    io.minio.StatObjectArgs.builder()
                                            .bucket(BUCKET)
                                            .object(file2)
                                            .build()
                            );
                            // Compare by last modified time (latest first)
                            return stat2.lastModified().compareTo(stat1.lastModified());
                        } catch (Exception e) {
                            System.err.println("[ERROR] Error comparing file times: " + e.getMessage());
                            return 0;
                        }
                    });
                    
                    // Only take the first file (most recent by LastModified)
                    if (!allParquetFiles.isEmpty()) {
                        String latestFile = allParquetFiles.get(0);
                        
                        // Count how many emails from this file are already in DB
                        // If all exist, skip. If some exist, they'll be skipped during insertion
                        parquetFiles.add(latestFile);
                        System.out.println("[INFO] Processing only latest file for user " + userEmail + " (date " + latestDate + "): " + latestFile);
                    }
                    
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

                    String tempPath = "temp.parquet";
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

                        // Truncate text to respect OpenAI token limit (8192)
                        // ~4 characters per token on average, so max ~30000 characters
                        final int MAX_CHARS = 25000;
                        if (subject.length() > MAX_CHARS) {
                            subject = subject.substring(0, MAX_CHARS);
                        }
                        if (body.length() > MAX_CHARS) {
                            body = body.substring(0, MAX_CHARS);
                        }

                        // 4. Generate embeddings using Spring AI
                        String subjectEmbStr = generateEmbedding(subject);
                        String bodyEmbStr = generateEmbedding(body);

                        // 5. Save with native SQL query (including original text for RAG)
                        String sender = Objects.toString(record.get("from"), "");
                        String receiver = Objects.toString(record.get("to"), "");
                        String senderDomain = Objects.toString(record.get("sender_domain"), "");
                        boolean isImportant = Boolean.parseBoolean(
                                Objects.toString(record.get("is_important"), "false")
                        );

                        // Insert with userId for multi-user isolation
                        emailRepository.insertWithEmbeddingsAndUserId(
                                id, userId, sender, receiver, senderDomain, isImportant,
                                subject, body,
                                subjectEmbStr, bodyEmbStr
                        );

                    }

                    reader.close();

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


    @Override
    public List<EmailEmbedding> getAllEmbeddings() {
        return emailRepository.findAll();
    }
}