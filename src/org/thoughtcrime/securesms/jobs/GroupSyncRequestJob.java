package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.inject.Inject;

import androidx.work.Data;

/**
 * Created by Benni on 12.07.2016.
 */
public class GroupSyncRequestJob extends ContextJob implements InjectableType {
    @Inject transient SignalServiceMessageSender messageSender;

    public GroupSyncRequestJob(Context context) {
        super(context, JobParameters.newBuilder()
                .withNetworkRequirement()
                .create());

    }


    @Override
    public void onRun() throws Exception {
        SignalServiceProtos.SyncMessage.Request groupSyncRequest = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        SignalServiceSyncMessage groupSyncRequestMessage = SignalServiceSyncMessage.forRequest(new RequestMessage(groupSyncRequest));

        try {
            messageSender.sendMessage(groupSyncRequestMessage, Optional.absent());
        } catch(Exception e) {
            Log.w("GroupSyncRequestJob", e);
        }
    }

    @Override
    public boolean onShouldRetry(Exception exception) {
        return false;
    }

    @Override
    public void onAdded() {

    }

    @NonNull
    @Override
    protected Data serialize(@NonNull Data.Builder dataBuilder) {
        return null;
    }

    @Override
    protected void initialize(@NonNull SafeData data) {

    }

    @Override
    public void onCanceled() {

    }
}
