package org.thoughtcrime.securesms.jobs;

import android.util.Log;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import javax.inject.Inject;

/**
 * Created by Benni on 12.07.2016.
 */
public class ContactSyncRequestJob extends BaseJob implements InjectableType {
    @Inject transient SignalServiceMessageSender messageSender;

    public static final String KEY = "ContactSyncRequestJob";

    public ContactSyncRequestJob() {
    	super(new Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .build());
    }


    @Override
    public void onRun() throws Exception {
        SignalServiceProtos.SyncMessage.Request contactSyncRequest = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        SignalServiceSyncMessage contactSyncRequestMessage = SignalServiceSyncMessage.forRequest(new RequestMessage(contactSyncRequest));

        try {
            messageSender.sendMessage(contactSyncRequestMessage, Optional.absent());
        } catch(Exception e) {
            Log.w("ContactSyncRequestJob", e);
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

    public static final class Factory implements Job.Factory<ContactSyncRequestJob> {
        @Override
        public @NonNull
        ContactSyncRequestJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new ContactSyncRequestJob();
        }
    }
}
