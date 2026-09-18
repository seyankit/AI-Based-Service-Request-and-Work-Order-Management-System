package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import ph.edu.htcgsc.serviceportal.dao.PersonnelDAO;
import ph.edu.htcgsc.serviceportal.model.Personnel;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ProfilePhotoServlet extends HttpServlet {

    private static final Gson GSON =
            new Gson();

    private static final long MAX_FILE_SIZE =
            5L * 1024L * 1024L;

    private static final String STORAGE_ENVIRONMENT_VARIABLE =
            "HTC_PROFILE_IMAGE_DIR";

    private final PersonnelDAO personnelDAO =
            new PersonnelDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!SessionUtil.isAuthenticated(request)) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );
            return;
        }

        Integer personnelId =
                SessionUtil.getAuthenticatedPersonnelId(
                        request
                );

        if (personnelId == null) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );
            return;
        }

        try {
            Personnel personnel =
                    personnelDAO.findById(personnelId);

            if (personnel == null) {
                sendJsonError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Personnel account was not found."
                );
                return;
            }

            String fileName =
                    personnel.getProfileImageFileName();

            if (
                fileName == null
                || fileName.isBlank()
            ) {
                sendJsonError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "No profile photo is available."
                );
                return;
            }

            Path storageDirectory =
                    getStorageDirectory();

            Path photoPath =
                    resolveStoredFile(
                            storageDirectory,
                            fileName
                    );

            if (
                photoPath == null
                || !Files.isRegularFile(photoPath)
            ) {
                sendJsonError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "The profile photo file is unavailable."
                );
                return;
            }

            String contentType =
                    contentTypeForFileName(
                            fileName
                    );

            if (contentType == null) {
                sendJsonError(
                        response,
                        HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "The stored profile photo type is invalid."
                );
                return;
            }

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.setContentType(
                    contentType
            );

            response.setContentLengthLong(
                    Files.size(photoPath)
            );

            response.setHeader(
                    "Cache-Control",
                    "no-store, no-cache, must-revalidate"
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
                    photoPath,
                    response.getOutputStream()
            );

        } catch (SQLException exception) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load the profile photo."
            );

        } catch (IllegalStateException exception) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    exception.getMessage()
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
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendJsonError(
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
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );
            return;
        }

        Part photoPart;

        try {
            photoPart =
                    request.getPart("photo");
        } catch (IllegalStateException exception) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Profile photos must not exceed 5 MB."
            );
            return;
        } catch (ServletException exception) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Unable to read the uploaded profile photo."
            );
            return;
        }

        if (
            photoPart == null
            || photoPart.getSize() <= 0
        ) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a profile photo to upload."
            );
            return;
        }

        if (photoPart.getSize() > MAX_FILE_SIZE) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Profile photos must not exceed 5 MB."
            );
            return;
        }

        byte[] imageBytes;

        try (InputStream input =
                     photoPart.getInputStream()) {

            imageBytes =
                    input.readAllBytes();
        }

        ImageType imageType =
                detectImageType(imageBytes);

        if (imageType == null) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Only valid JPG, PNG, or WEBP images are allowed."
            );
            return;
        }

        String newFileName =
                UUID.randomUUID()
                        .toString()
                        .toLowerCase()
                        + imageType.extension;

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
                            ".profile-upload-",
                            ".tmp"
                    );

            Files.write(
                    temporaryFile,
                    imageBytes
            );

            finalFile =
                    storageDirectory.resolve(
                            newFileName
                    ).normalize();

            if (
                !finalFile.getParent()
                        .equals(storageDirectory)
            ) {
                throw new IllegalStateException(
                        "Invalid profile photo storage path."
                );
            }

            Files.move(
                    temporaryFile,
                    finalFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            temporaryFile = null;

            Personnel personnel =
                    personnelDAO.findById(
                            personnelId
                    );

            if (personnel == null) {
                Files.deleteIfExists(
                        finalFile
                );

                sendJsonError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Personnel account was not found."
                );
                return;
            }

            String previousFileName =
                    personnel.getProfileImageFileName();

            boolean updated =
                    personnelDAO
                            .updateProfileImageFileName(
                                    personnelId,
                                    newFileName
                            );

            if (!updated) {
                Files.deleteIfExists(
                        finalFile
                );

                sendJsonError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Unable to save the profile photo."
                );
                return;
            }

            deleteStoredFileIfSafe(
                    storageDirectory,
                    previousFileName
            );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Profile photo updated successfully."
            );

            result.put(
                    "profileImageFileName",
                    newFileName
            );

            result.put(
                    "photoUrl",
                    request.getContextPath()
                            + "/api/profile/photo"
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
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

            sendJsonError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the profile photo."
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

            sendJsonError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    exception.getMessage()
            );
        }
    }

    @Override
    protected void doDelete(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!SessionUtil.isAuthenticated(request)) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendJsonError(
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
            sendJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "The authenticated session is invalid."
            );
            return;
        }

        try {
            Personnel personnel =
                    personnelDAO.findById(
                            personnelId
                    );

            if (personnel == null) {
                sendJsonError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Personnel account was not found."
                );
                return;
            }

            String existingFileName =
                    personnel.getProfileImageFileName();

            boolean updated =
                    personnelDAO
                            .updateProfileImageFileName(
                                    personnelId,
                                    null
                            );

            if (!updated) {
                sendJsonError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Unable to remove the profile photo."
                );
                return;
            }

            Path storageDirectory =
                    getStorageDirectory();

            deleteStoredFileIfSafe(
                    storageDirectory,
                    existingFileName
            );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Profile photo removed successfully."
            );

            result.put(
                    "profileImageFileName",
                    null
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    GSON.toJson(result)
            );

        } catch (SQLException exception) {
            sendJsonError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to remove the profile photo."
            );

        } catch (IllegalStateException exception) {
            sendJsonError(
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
                    "Profile image storage is not configured."
            );
        }

        return Path.of(
                configuredPath.trim()
        ).toAbsolutePath().normalize();
    }

    private Path resolveStoredFile(
            Path storageDirectory,
            String fileName
    ) {

        if (
            fileName == null
            || fileName.isBlank()
            || fileName.contains("/")
            || fileName.contains("\\")
        ) {
            return null;
        }

        Path resolved =
                storageDirectory
                        .resolve(fileName)
                        .normalize();

        if (
            !resolved.getParent()
                    .equals(storageDirectory)
        ) {
            return null;
        }

        return resolved;
    }

    private void deleteStoredFileIfSafe(
            Path storageDirectory,
            String fileName
    ) throws IOException {

        Path storedFile =
                resolveStoredFile(
                        storageDirectory,
                        fileName
                );

        if (storedFile != null) {
            Files.deleteIfExists(
                    storedFile
            );
        }
    }

    private ImageType detectImageType(
            byte[] data
    ) {

        if (
            data == null
            || data.length < 12
        ) {
            return null;
        }

        if (
            (data[0] & 0xFF) == 0xFF
            && (data[1] & 0xFF) == 0xD8
            && (data[2] & 0xFF) == 0xFF
        ) {
            return ImageType.JPEG;
        }

        if (
            (data[0] & 0xFF) == 0x89
            && data[1] == 0x50
            && data[2] == 0x4E
            && data[3] == 0x47
            && data[4] == 0x0D
            && data[5] == 0x0A
            && data[6] == 0x1A
            && data[7] == 0x0A
        ) {
            return ImageType.PNG;
        }

        if (
            data[0] == 'R'
            && data[1] == 'I'
            && data[2] == 'F'
            && data[3] == 'F'
            && data[8] == 'W'
            && data[9] == 'E'
            && data[10] == 'B'
            && data[11] == 'P'
        ) {
            return ImageType.WEBP;
        }

        return null;
    }

    private String contentTypeForFileName(
            String fileName
    ) {

        String lower =
                fileName.toLowerCase();

        if (
            lower.endsWith(".jpg")
            || lower.endsWith(".jpeg")
        ) {
            return "image/jpeg";
        }

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".webp")) {
            return "image/webp";
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

    private void sendJsonError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        prepareJsonResponse(response);

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

    private enum ImageType {

        JPEG(".jpg"),
        PNG(".png"),
        WEBP(".webp");

        private final String extension;

        ImageType(
                String extension
        ) {
            this.extension =
                    extension;
        }
    }
}