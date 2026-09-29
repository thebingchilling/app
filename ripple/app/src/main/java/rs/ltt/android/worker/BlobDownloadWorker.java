package rs.ltt.android.worker;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkInfo;
import androidx.work.WorkerParameters;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
import java.io.File;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.cache.BlobStorage;
import rs.ltt.android.entity.DownloadableBlob;
import rs.ltt.android.ui.notification.AttachmentNotification;
import rs.ltt.android.mail.model.Downloadable;

public class BlobDownloadWorker extends AbstractMuaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobDownloadWorker.class);

    private static final String EMAIL_ID_KEY = "emailId";
    private static final String BLOB_ID_KEY = "blobId";
    private static final String URI_KEY = "uri";

    private final String emailId;
    private final String blobId;
    private final NotificationManager notificationManager;
    private final RateLimiter notificationRateLimiter = RateLimiter.create(1);
    private final ListenableFuture<DownloadableBlob> downloadable;
    private ListenableFuture<?> cancelableFuture;
    private int currentlyShownProgress = Integer.MIN_VALUE;

    public BlobDownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.emailId = data.getString(EMAIL_ID_KEY);
        this.blobId = data.getString(BLOB_ID_KEY);
        this.notificationManager = context.getSystemService(NotificationManager.class);
        this.downloadable = getDatabase().threadAndEmailDao().getDownloadable(emailId, blobId);
    }

    public static Uri getUri(final WorkInfo workInfo) {
        Preconditions.checkState(
                workInfo.getState() == WorkInfo.State.SUCCEEDED,
                "Work must have succeeded to extract uri");
        final Data data = workInfo.getOutputData();
        final String uri =
                Preconditions.checkNotNull(data.getString(URI_KEY), "OutputData is missing URI");
        return Uri.parse(uri);
    }

    public static Data data(final Long account, final String emailId, final String blobId) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putString(EMAIL_ID_KEY, emailId)
                .putString(BLOB_ID_KEY, blobId)
                .build();
    }

    public static String uniqueName() {
        return "blob-download";
    }

    @NonNull
    @Override
    public Result doWork() {
        final Downloadable downloadable;
        try {
            downloadable = this.downloadable.get();
        } catch (final ExecutionException | InterruptedException e) {
            return Result.failure();
        }
        if (downloadable == null) {
            LOGGER.error("Unable to download blob {}. E-mail {} does not exist", blobId, emailId);
            return Result.failure();
        }
        // begin to display notification even if we don’t run as ForegroundService on Android 12
        updateProgress(downloadable, 0, true);
        final ListenableFuture<File> downloadFuture = getMua().download(blobId);
        this.cancelableFuture = downloadFuture;
        try {
            downloadFuture.get();
            notifyDownloadComplete(downloadable);
            final BlobStorage storage = BlobStorage.get(getApplicationContext(), account, blobId);
            return getResult(downloadable, storage);
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            LOGGER.warn("Unable to download attachment", cause);
            if (isNetworkIssue(cause)) {
                return Result.retry();
            }
            return Result.failure(Failure.of(cause));
        } catch (final InterruptedException e) {
            return Result.retry();
        } finally {
            notificationManager.cancel(AttachmentNotification.DOWNLOAD_ID);
        }
    }

    private Result getResult(final Downloadable downloadable, final BlobStorage storage) {
        final Uri uri =
                BlobStorage.getFileProviderUri(
                        getApplicationContext(), storage.file, downloadable.getName());
        final Data data =
                new Data.Builder()
                        .putString(URI_KEY, uri.toString()) // to be picked up by view intent
                        .putString(
                                StoreAttachmentWorker.FILE_KEY,
                                storage.file.getAbsolutePath()) // to be picked up by
                        // StoreAttachmentWorker
                        .build();
        return Result.success(data);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (this.cancelableFuture != null) {
            if (this.cancelableFuture.cancel(true)) {
                LOGGER.info("Cancelled download future");
            }
        }
    }

    @NonNull
    @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
        return Futures.transform(
                downloadable,
                downloadable -> {
                    final Notification notification;
                    if (downloadable == null) {
                        notification =
                                AttachmentNotification.emailNotCached(getApplicationContext());
                    } else {
                        notification =
                                AttachmentNotification.downloading(
                                        getApplicationContext(), getId(), downloadable, 0, true);
                    }
                    return new ForegroundInfo(AttachmentNotification.DOWNLOAD_ID, notification);
                },
                MoreExecutors.directExecutor());
    }

    private void notifyDownloadComplete(final Downloadable downloadable) {
        getDatabase()
                .threadAndEmailDao()
                .incrementEmailBodyPartDownloadCount(this.emailId, downloadable.getBlobId());
        notificationManager.notify(
                AttachmentNotification.DOWNLOAD_ID,
                AttachmentNotification.downloaded(getApplicationContext(), downloadable));
    }

    private void updateProgress(
            final Downloadable downloadable, final int progress, final boolean indeterminate) {
        if (currentlyShownProgress == progress) {
            return;
        }
        if (notificationRateLimiter.tryAcquire()) {
            notificationManager.notify(
                    AttachmentNotification.DOWNLOAD_ID,
                    AttachmentNotification.downloading(
                            getApplicationContext(),
                            getId(),
                            downloadable,
                            progress,
                            indeterminate));
            this.currentlyShownProgress = progress;
        }
    }
}
