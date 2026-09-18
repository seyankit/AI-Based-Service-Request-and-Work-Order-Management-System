package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import ph.edu.htcgsc.serviceportal.dao.AttachmentDAO;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServiceRequestAttachmentServlet
        extends HttpServlet {

    private static final Gson GSON =
            new Gson();

    private static final long MAX_FILE_SIZE =
            5L * 1024L * 1024L;

    private static final String STORAGE_ENVIRONMENT_VARIABLE =
            "HTC_ATTACHMENT_DIR";

    private final AttachmentDAO attachmentDAO =
            new AttachmentDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!SessionUtil.isAuthenticated(request)) {
            prepareJsonResponse(response);

            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );

            return;
        }

        if (!SessionUtil.isRequester(request)) {
            prepareJsonResponse(response);

            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Only requester accounts may access requester attachments."
            );

            return;
        }

        Integer personnelId =
                SessionUtil.getAuthenticatedPersonnelId(
                        request
                );

        if (personnelId == null) {
            prepareJsonResponse(response);

            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );

            return;
        }

        String attachmentIdText =
                request.getParameter(
                        "attachmentId"
                );

        String requestIdText =
                request.getParameter(
                        "requestId"
                );

        /*
         * Download one requester-owned attachment.
         */
        if (
            attachmentIdText != null
            && !attachmentIdText.isBlank()
        ) {
            long attachmentId;

            try {
                attachmentId =
                        Long.parseLong(
                                attachmentIdText.trim()
                        );

                if (attachmentId <= 0) {
                    throw new NumberFormatException();
                }

            } catch (NumberFormatException exception) {
                prepareJsonResponse(response);

                sendError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "A valid attachment ID is required."
                );

                return;
            }

            try {
                AttachmentDAO.RequestAttachment attachment =
                        attachmentDAO
                                .findActiveRequestAttachmentForRequester(
                                        attachmentId,
                                        personnelId
                                );

                if (attachment == null) {
                    prepareJsonResponse(response);

                    sendError(
                            response,
                            HttpServletResponse.SC_NOT_FOUND,
                            "Attachment not found."
                    );

                    return;
                }

                String storedFileName =
                        attachment.getStoredFileName();

                if (
                    storedFileName == null
                    || storedFileName.isBlank()
                    || storedFileName.contains("/")
                    || storedFileName.contains("\\")
                ) {
                    prepareJsonResponse(response);

                    sendError(
                            response,
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "The attachment storage reference is invalid."
                    );

                    return;
                }

                Path storageDirectory =
                        getStorageDirectory();

                Path file =
                        storageDirectory
                                .resolve(
                                        storedFileName
                                )
                                .normalize();

                if (
                    !storageDirectory.equals(
                            file.getParent()
                    )
                ) {
                    prepareJsonResponse(response);

                    sendError(
                            response,
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "The attachment storage path is invalid."
                    );

                    return;
                }

                if (!Files.isRegularFile(file)) {
                    prepareJsonResponse(response);

                    sendError(
                            response,
                            HttpServletResponse.SC_NOT_FOUND,
                            "The attachment file is unavailable."
                    );

                    return;
                }

                String originalFileName =
                        attachment.getOriginalFileName();

                if (
                    originalFileName == null
                    || originalFileName.isBlank()
                ) {
                    originalFileName =
                            "attachment."
                            + attachment.getFileExtension();
                }

                /*
                 * Remove control characters before using the
                 * original name in an HTTP response header.
                 */
                originalFileName =
                        originalFileName.replaceAll(
                                "[\\r\\n\\p{Cntrl}]",
                                ""
                        );

                String encodedFileName =
                        URLEncoder.encode(
                                originalFileName,
                                StandardCharsets.UTF_8
                        ).replace(
                                "+",
                                "%20"
                        );

                response.reset();

                response.setContentType(
                        attachment.getContentType()
                );

                response.setCharacterEncoding(
                        "UTF-8"
                );

                response.setContentLengthLong(
                        Files.size(file)
                );

                response.setHeader(
                        "Content-Disposition",
                        "attachment; filename*=UTF-8''"
                                + encodedFileName
                );

                response.setHeader(
                        "Cache-Control",
                        "private, no-store, no-cache, must-revalidate"
                );

                response.setHeader(
                        "Pragma",
                        "no-cache"
                );

                response.setHeader(
                        "X-Content-Type-Options",
                        "nosniff"
                );

                Files.copy(
                        file,
                        response.getOutputStream()
                );

                return;

            } catch (SQLException exception) {
                prepareJsonResponse(response);

                sendError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Unable to load the attachment."
                );

                return;

            } catch (IllegalStateException exception) {
                prepareJsonResponse(response);

                sendError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        exception.getMessage()
                );

                return;
            }
        }

        /*
         * Otherwise return metadata for the request.
         */
        long requestId;

        try {
            requestId =
                    Long.parseLong(
                            requestIdText == null
                                    ? ""
                                    : requestIdText.trim()
                    );

            if (requestId <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException exception) {
            prepareJsonResponse(response);

            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "A valid service request ID is required."
            );

            return;
        }

        try {
            List<AttachmentDAO.RequestAttachment> attachments =
                    attachmentDAO
                            .findActiveRequestAttachmentsForRequester(
                                    requestId,
                                    personnelId
                            );

            List<Map<String, Object>> items =
                    attachments.stream()
                            .map(
                                    attachment -> {
                                        Map<String, Object> item =
                                                new LinkedHashMap<>();

                                        item.put(
                                                "attachmentId",
                                                attachment.getAttachmentId()
                                        );

                                        item.put(
                                                "requestId",
                                                attachment.getRequestId()
                                        );

                                        item.put(
                                                "originalFileName",
                                                attachment.getOriginalFileName()
                                        );

                                        item.put(
                                                "contentType",
                                                attachment.getContentType()
                                        );

                                        item.put(
                                                "fileSizeBytes",
                                                attachment.getFileSizeBytes()
                                        );

                                        item.put(
                                                "uploadedAt",
                                                attachment.getUploadedAt()
                                        );

                                        item.put(
                                                "downloadUrl",
                                                "api/service-request-attachments?attachmentId="
                                                        + attachment.getAttachmentId()
                                                        + "&download=1"
                                        );

                                        return item;
                                    }
                            )
                            .toList();

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "requestId",
                    requestId
            );

            result.put(
                    "attachments",
                    items
            );

            prepareJsonResponse(response);

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    GSON.toJson(result)
            );

        } catch (SQLException exception) {
            prepareJsonResponse(response);

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load request attachments."
            );
        }
    }
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );
            return;
        }

        if (!SessionUtil.isRequester(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Only requester accounts may upload request evidence."
            );
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again."
            );
            return;
        }

        Integer personnelId =
                SessionUtil.getAuthenticatedPersonnelId(
                        request
                );

        if (personnelId == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );
            return;
        }

        long requestId;

        try {
            String requestIdText =
                    request.getParameter(
                            "requestId"
                    );

            requestId =
                    Long.parseLong(
                            requestIdText == null
                                    ? ""
                                    : requestIdText.trim()
                    );

            if (requestId <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "A valid service request ID is required."
            );
            return;
        }

        Part attachmentPart;

        try {
            attachmentPart =
                    request.getPart(
                            "attachment"
                    );

        } catch (IllegalStateException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Attachments must not exceed 5 MB."
            );
            return;

        } catch (ServletException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Unable to read the uploaded attachment."
            );
            return;
        }

        if (
            attachmentPart == null
            || attachmentPart.getSize() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a file to upload."
            );
            return;
        }

        if (attachmentPart.getSize() > MAX_FILE_SIZE) {
            sendError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Attachments must not exceed 5 MB."
            );
            return;
        }

        byte[] fileBytes;

        try (
            InputStream inputStream =
                    attachmentPart.getInputStream()
        ) {
            fileBytes =
                    inputStream.readAllBytes();
        }

        FileType fileType =
                detectFileType(
                        fileBytes
                );

        if (fileType == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Only valid PNG, JPG, WEBP, or PDF files are allowed."
            );
            return;
        }

        String originalFileName =
                sanitizeOriginalFileName(
                        attachmentPart
                                .getSubmittedFileName(),
                        fileType.extension
                );

        String storedFileName =
                UUID.randomUUID()
                        .toString()
                        .toLowerCase()
                        + "."
                        + fileType.extension;

        String sha256;

        try {
            sha256 =
                    calculateSha256(
                            fileBytes
                    );

        } catch (NoSuchAlgorithmException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to verify the uploaded attachment."
            );
            return;
        }

        Path temporaryFile = null;
        Path finalFile = null;

        try {
            Path storageDirectory =
                    getStorageDirectory();

            Files.createDirectories(
                    storageDirectory
            );

            temporaryFile =
                    Files.createTempFile(
                            storageDirectory,
                            ".request-upload-",
                            ".tmp"
                    );

            Files.write(
                    temporaryFile,
                    fileBytes
            );

            finalFile =
                    storageDirectory
                            .resolve(
                                    storedFileName
                            )
                            .normalize();

            if (
                !storageDirectory.equals(
                        finalFile.getParent()
                )
            ) {
                throw new IllegalStateException(
                        "Invalid attachment storage path."
                );
            }

            Files.move(
                    temporaryFile,
                    finalFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            temporaryFile = null;

            long attachmentId =
                    attachmentDAO
                            .createRequestAttachment(
                                    requestId,
                                    personnelId,
                                    originalFileName,
                                    storedFileName,
                                    fileType.extension,
                                    fileType.contentType,
                                    fileBytes.length,
                                    sha256
                            );

            if (attachmentId <= 0) {
                Files.deleteIfExists(
                        finalFile
                );

                sendError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "The service request was not found or is not owned by the signed-in requester."
                );
                return;
            }

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Attachment uploaded successfully."
            );

            result.put(
                    "attachmentId",
                    attachmentId
            );

            result.put(
                    "requestId",
                    requestId
            );

            result.put(
                    "originalFileName",
                    originalFileName
            );

            result.put(
                    "contentType",
                    fileType.contentType
            );

            result.put(
                    "fileSizeBytes",
                    fileBytes.length
            );

            response.setStatus(
                    HttpServletResponse.SC_CREATED
            );

            response.getWriter().write(
                    GSON.toJson(result)
            );

        } catch (SQLException exception) {

            if (finalFile != null) {
                Files.deleteIfExists(
                        finalFile
                );
            }

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the attachment record."
            );

        } catch (IllegalStateException exception) {

            if (temporaryFile != null) {
                Files.deleteIfExists(
                        temporaryFile
                );
            }

            if (finalFile != null) {
                Files.deleteIfExists(
                        finalFile
                );
            }

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    exception.getMessage()
            );
        }
    }

    private Path getStorageDirectory() {

        String configuredPath =
                System.getenv(
                        STORAGE_ENVIRONMENT_VARIABLE
                );

        if (
            configuredPath == null
            || configuredPath.isBlank()
        ) {
            throw new IllegalStateException(
                    "Attachment storage is not configured."
            );
        }

        return Path.of(
                configuredPath.trim()
        ).toAbsolutePath().normalize();
    }

    private String sanitizeOriginalFileName(
            String submittedFileName,
            String fallbackExtension
    ) {

        String value =
                submittedFileName == null
                        ? ""
                        : submittedFileName.trim();

        value =
                value.replace(
                        '\\',
                        '/'
                );

        int slashIndex =
                value.lastIndexOf('/');

        if (slashIndex >= 0) {
            value =
                    value.substring(
                            slashIndex + 1
                    );
        }

        value =
                value.replaceAll(
                        "[\\p{Cntrl}]",
                        ""
                ).trim();

        if (value.isBlank()) {
            value =
                    "attachment."
                    + fallbackExtension;
        }

        if (value.length() > 255) {
            value =
                    value.substring(
                            0,
                            255
                    );
        }

        return value;
    }

    private String calculateSha256(
            byte[] data
    ) throws NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        return HexFormat.of()
                .formatHex(
                        digest.digest(data)
                );
    }

    private FileType detectFileType(
            byte[] data
    ) {

        if (
            data == null
            || data.length < 4
        ) {
            return null;
        }

        if (
            data.length >= 8
            && (data[0] & 0xFF) == 0x89
            && data[1] == 0x50
            && data[2] == 0x4E
            && data[3] == 0x47
            && data[4] == 0x0D
            && data[5] == 0x0A
            && data[6] == 0x1A
            && data[7] == 0x0A
        ) {
            return FileType.PNG;
        }

        if (
            (data[0] & 0xFF) == 0xFF
            && (data[1] & 0xFF) == 0xD8
            && (data[2] & 0xFF) == 0xFF
        ) {
            return FileType.JPEG;
        }

        if (
            data.length >= 12
            && data[0] == 'R'
            && data[1] == 'I'
            && data[2] == 'F'
            && data[3] == 'F'
            && data[8] == 'W'
            && data[9] == 'E'
            && data[10] == 'B'
            && data[11] == 'P'
        ) {
            return FileType.WEBP;
        }

        if (
            data[0] == '%'
            && data[1] == 'P'
            && data[2] == 'D'
            && data[3] == 'F'
        ) {
            return FileType.PDF;
        }

        return null;
    }

    private void prepareJsonResponse(
            HttpServletResponse response
    ) {
        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate"
        );

        response.setHeader(
                "Pragma",
                "no-cache"
        );
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        prepareJsonResponse(
                response
        );

        response.setStatus(
                status
        );

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "success",
                false
        );

        body.put(
                "message",
                message
        );

        response.getWriter().write(
                GSON.toJson(body)
        );
    }

    private enum FileType {

        PNG(
                "png",
                "image/png"
        ),

        JPEG(
                "jpg",
                "image/jpeg"
        ),

        WEBP(
                "webp",
                "image/webp"
        ),

        PDF(
                "pdf",
                "application/pdf"
        );

        private final String extension;
        private final String contentType;

        FileType(
                String extension,
                String contentType
        ) {
            this.extension =
                    extension;

            this.contentType =
                    contentType;
        }
    }
}