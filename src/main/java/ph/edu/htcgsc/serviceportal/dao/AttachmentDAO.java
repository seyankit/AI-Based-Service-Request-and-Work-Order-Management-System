package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AttachmentDAO {

    public long createRequestAttachment(
            long requestId,
            int requesterId,
            String originalFileName,
            String storedFileName,
            String fileExtension,
            String contentType,
            long fileSizeBytes,
            String fileSha256
    ) throws SQLException {

        String ownershipSql = """
                SELECT 1
                FROM SERVICE_REQUEST
                WHERE Request_ID = ?
                  AND Requester_ID = ?
                """;

        String insertSql = """
                INSERT INTO ATTACHMENT (
                    Request_ID,
                    Progress_ID,
                    Uploaded_By,
                    Attachment_Purpose,
                    Original_File_Name,
                    Stored_File_Name,
                    File_Extension,
                    Content_Type,
                    File_Size_Bytes,
                    File_SHA256,
                    Attachment_Description,
                    Is_Requester_Visible,
                    Attachment_Status
                )
                VALUES (
                    ?,
                    NULL,
                    ?,
                    'Request Evidence',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    NULL,
                    1,
                    'Active'
                )
                """;

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            boolean previousAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                try (
                    PreparedStatement ownershipStatement =
                            connection.prepareStatement(
                                    ownershipSql
                            )
                ) {
                    ownershipStatement.setLong(
                            1,
                            requestId
                    );

                    ownershipStatement.setInt(
                            2,
                            requesterId
                    );

                    try (
                        ResultSet resultSet =
                                ownershipStatement.executeQuery()
                    ) {
                        if (!resultSet.next()) {
                            connection.rollback();

                            return 0L;
                        }
                    }
                }

                long attachmentId;

                try (
                    PreparedStatement insertStatement =
                            connection.prepareStatement(
                                    insertSql,
                                    Statement.RETURN_GENERATED_KEYS
                            )
                ) {
                    insertStatement.setLong(
                            1,
                            requestId
                    );

                    insertStatement.setInt(
                            2,
                            requesterId
                    );

                    insertStatement.setString(
                            3,
                            originalFileName
                    );

                    insertStatement.setString(
                            4,
                            storedFileName
                    );

                    insertStatement.setString(
                            5,
                            fileExtension
                    );

                    insertStatement.setString(
                            6,
                            contentType
                    );

                    insertStatement.setLong(
                            7,
                            fileSizeBytes
                    );

                    insertStatement.setString(
                            8,
                            fileSha256
                    );

                    if (
                        insertStatement.executeUpdate()
                        != 1
                    ) {
                        throw new SQLException(
                                "Attachment insert failed."
                        );
                    }

                    try (
                        ResultSet generatedKeys =
                                insertStatement
                                        .getGeneratedKeys()
                    ) {
                        if (!generatedKeys.next()) {
                            throw new SQLException(
                                    "Missing generated Attachment_ID."
                            );
                        }

                        attachmentId =
                                generatedKeys.getLong(1);
                    }
                }

                connection.commit();

                return attachmentId;

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(
                        previousAutoCommit
                );
            }
        }
    }

    public List<RequestAttachment> findActiveRequestAttachmentsForRequester(
            long requestId,
            int requesterId
    ) throws SQLException {

        String sql = """
                SELECT
                    a.Attachment_ID,
                    a.Request_ID,
                    a.Original_File_Name,
                    a.Stored_File_Name,
                    a.File_Extension,
                    a.Content_Type,
                    a.File_Size_Bytes,
                    a.Uploaded_At
                FROM ATTACHMENT a
                INNER JOIN SERVICE_REQUEST sr
                    ON sr.Request_ID = a.Request_ID
                WHERE a.Request_ID = ?
                  AND sr.Requester_ID = ?
                  AND a.Progress_ID IS NULL
                  AND a.Attachment_Purpose = 'Request Evidence'
                  AND a.Attachment_Status = 'Active'
                  AND a.Is_Requester_Visible = TRUE
                ORDER BY
                    a.Uploaded_At ASC,
                    a.Attachment_ID ASC
                """;

        List<RequestAttachment> attachments =
                new ArrayList<>();

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setLong(
                    1,
                    requestId
            );

            statement.setInt(
                    2,
                    requesterId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                while (resultSet.next()) {
                    attachments.add(
                            mapRequestAttachment(
                                    resultSet
                            )
                    );
                }
            }
        }

        return attachments;
    }

    public RequestAttachment findActiveRequestAttachmentForRequester(
            long attachmentId,
            int requesterId
    ) throws SQLException {

        String sql = """
                SELECT
                    a.Attachment_ID,
                    a.Request_ID,
                    a.Original_File_Name,
                    a.Stored_File_Name,
                    a.File_Extension,
                    a.Content_Type,
                    a.File_Size_Bytes,
                    a.Uploaded_At
                FROM ATTACHMENT a
                INNER JOIN SERVICE_REQUEST sr
                    ON sr.Request_ID = a.Request_ID
                WHERE a.Attachment_ID = ?
                  AND sr.Requester_ID = ?
                  AND a.Progress_ID IS NULL
                  AND a.Attachment_Purpose = 'Request Evidence'
                  AND a.Attachment_Status = 'Active'
                  AND a.Is_Requester_Visible = TRUE
                LIMIT 1
                """;

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setLong(
                    1,
                    attachmentId
            );

            statement.setInt(
                    2,
                    requesterId
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    return null;
                }

                return mapRequestAttachment(
                        resultSet
                );
            }
        }
    }

    private RequestAttachment mapRequestAttachment(
            ResultSet resultSet
    ) throws SQLException {

        RequestAttachment attachment =
                new RequestAttachment();

        attachment.attachmentId =
                resultSet.getLong(
                        "Attachment_ID"
                );

        attachment.requestId =
                resultSet.getLong(
                        "Request_ID"
                );

        attachment.originalFileName =
                resultSet.getString(
                        "Original_File_Name"
                );

        attachment.storedFileName =
                resultSet.getString(
                        "Stored_File_Name"
                );

        attachment.fileExtension =
                resultSet.getString(
                        "File_Extension"
                );

        attachment.contentType =
                resultSet.getString(
                        "Content_Type"
                );

        attachment.fileSizeBytes =
                resultSet.getLong(
                        "File_Size_Bytes"
                );

        attachment.uploadedAt =
                resultSet.getTimestamp(
                        "Uploaded_At"
                );

        return attachment;
    }

    public static class RequestAttachment {

        private long attachmentId;
        private long requestId;
        private String originalFileName;
        private String storedFileName;
        private String fileExtension;
        private String contentType;
        private long fileSizeBytes;
        private Timestamp uploadedAt;

        public long getAttachmentId() {
            return attachmentId;
        }

        public long getRequestId() {
            return requestId;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public String getStoredFileName() {
            return storedFileName;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getContentType() {
            return contentType;
        }

        public long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public Timestamp getUploadedAt() {
            return uploadedAt;
        }
    }
}