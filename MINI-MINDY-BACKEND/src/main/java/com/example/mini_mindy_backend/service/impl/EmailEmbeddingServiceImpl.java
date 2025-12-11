package com.example.mini_mindy_backend.service.impl;


import com.example.mini_mindy_backend.model.EmailEmbedding;
import com.example.mini_mindy_backend.repository.EmailEmbeddingRepository;
import com.example.mini_mindy_backend.service.EmailEmbeddingService;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
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

@Service
public class EmailEmbeddingServiceImpl implements EmailEmbeddingService {

    private final EmailEmbeddingRepository emailRepository;
    private final EmbeddingModel embeddingModel;

    // MinIO config (port 9000 = S3 API, 9001 = Web Console)
    private final String MINIO_URL = "http://127.0.0.1:9000";
    private final String MINIO_ACCESS = "minioadmin";
    private final String MINIO_SECRET = "minioadmin123";
    private final String BUCKET = "datalake";

    public EmailEmbeddingServiceImpl(EmailEmbeddingRepository emailRepository, EmbeddingModel embeddingModel) {
        this.emailRepository = emailRepository;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void processEmailsFromMinIO() {
        try {
            // MinIO connection
            MinioClient minio = MinioClient.builder()
                    .endpoint(MINIO_URL)
                    .credentials(MINIO_ACCESS, MINIO_SECRET)
                    .build();

            List<String> parquetFiles = new ArrayList<>();

            // 1. Retrieve parquet files
            try {
                var results = minio.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(BUCKET)
                                .prefix("bronze/emails/")
                                .recursive(true)
                                .build()
                );

                for (var result : results) {
                    try {
                        String objectName = result.get().objectName();
                        // Filter only .parquet files
                        if (objectName.endsWith(".parquet")) {
                            parquetFiles.add(objectName);
                            System.out.println("[INFO] File found: " + objectName);
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Error reading object: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("[ERROR] MinIO listing error: " + e.getMessage());
                throw new RuntimeException("Unable to list MinIO objects", e);
            }

            System.out.println("[INFO] Parquet files detected: " + parquetFiles.size());

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

                        emailRepository.insertWithEmbeddings(
                                id, sender, receiver, senderDomain, isImportant,
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