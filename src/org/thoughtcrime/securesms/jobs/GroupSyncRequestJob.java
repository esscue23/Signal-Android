package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
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

/**
 * Created by Benni on 12.07.2016.
 */
public class GroupSyncRequestJob extends BaseJob implements InjectableType {
    @Inject transient SignalServiceMessageSender messageSender;

    public static final String KEY = "GroupSyncRequestJob";

    public GroupSyncRequestJob() {
    	super(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .build());
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
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public boolean onShouldRetry(Exception exception) {
        return false;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public @NonNull Data serialize() {
        return null;
    }

    protected void initialize(@NonNull Data data) {

    }

    @Override
    public void onCanceled() {

    }

    public static final class Factory implements Job.Factory<GroupSyncRequestJob> {
        @Override
        public @NonNull GroupSyncRequestJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new GroupSyncRequestJob();
        }
    }
}
