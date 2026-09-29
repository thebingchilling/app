package rs.ltt.android.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.cache.BlobStorage;
import rs.ltt.android.cache.LocalAttachment;
import rs.ltt.android.mail.model.Attachment;
import rs.ltt.android.mail.model.EmailBodyPart;

/**
 * Makes a picked attachment available to the email creation worker. With IMAP/SMTP attachments
 * are sent inside the message, so this only moves the file into the blob cache under a new id.
 */
public class BlobUploadWorker extends AbstractMuaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobUploadWorker.class);
    public static final String BLOB_ID_KEY = "blobId";
    private static final String NAME_KEY = "name";
    private static final String TYPE_KEY = "type";
    private static final String SIZE_KEY = "size";
    private static final String LOCAL_ATTACHMENT_UUID = "localAttachmentId";
    private final LocalAttachment localAttachment;

    public BlobUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        final UUID uuid =
                UUID.fromString(Objects.requireNonNull(data.getString(LOCAL_ATTACHMENT_UUID)));
        final String name = data.getString(NAME_KEY);
        final String type = Objects.requireNonNull(data.getString(TYPE_KEY));
        final long size = data.getLong(SIZE_KEY, 0);
        this.localAttachment = new LocalAttachment(uuid, MediaType.parse(type), name, size);
    }

    public static Data data(Long accountId, final LocalAttachment attachment) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, accountId)
                .putString(LOCAL_ATTACHMENT_UUID, attachment.getUuid().toString())
                .putString(NAME_KEY, attachment.getName())
                .putString(TYPE_KEY, attachment.getType())
                .putLong(SIZE_KEY, attachment.getSize())
                .build();
    }

    public static String uniqueName() {
        return "blob-upload";
    }

    public static Attachment getAttachment(final Data data) {
        return EmailBodyPart.builder()
                .blobId(data.getString(BLOB_ID_KEY))
                .type(data.getString(TYPE_KEY))
                .name(data.getString(NAME_KEY))
                .size(data.getLong(SIZE_KEY, 0))
                .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        final File file = LocalAttachment.asFile(getApplicationContext(), localAttachment);
        final String blobId = "local-" + localAttachment.getUuid();
        try {
            if (!file.exists()) {
                throw new IOException("Attachment file is gone");
            }
            cacheBlob(file, blobId);
            LocalAttachment.delete(getApplicationContext(), localAttachment);
            final Data data =
                    new Data.Builder()
                            .putString(BLOB_ID_KEY, blobId)
                            .putString(TYPE_KEY, localAttachment.getType())
                            .putString(NAME_KEY, localAttachment.getName())
                            .putLong(SIZE_KEY, localAttachment.getSize())
                            .build();
            return Result.success(data);
        } catch (final Exception e) {
            LOGGER.info("Failure preparing attachment", e);
            return Result.failure(Failure.of(e));
        }
    }

    private void cacheBlob(final File file, final String blobId) {
        final BlobStorage blobStorage = BlobStorage.get(getApplicationContext(), account, blobId);
        if (blobStorage.file.exists()) {
            LOGGER.info("Blob {} is already cached", blobId);
            return;
        }
        if (file.renameTo(blobStorage.file)) {
            LOGGER.info("Successfully cached blob {} by moving local attachment", blobId);
            return;
        }
        final long bytesCopied;
        try (final InputStream inputStream = new FileInputStream(file);
                final FileOutputStream fileOutputStream =
                        new FileOutputStream(blobStorage.temporaryFile)) {
            bytesCopied = ByteStreams.copy(inputStream, fileOutputStream);
            fileOutputStream.flush();
        } catch (final Exception e) {
            LOGGER.warn("Unable to write InputStream to blob cache", e);
            if (blobStorage.temporaryFile.delete()) {
                LOGGER.info("Deleted temporary file");
            }
            return;
        }
        if (blobStorage.moveTemporaryToFile()) {
            LOGGER.info("Successfully cached blob {}. {} bytes written", blobId, bytesCopied);
        }
    }
}
