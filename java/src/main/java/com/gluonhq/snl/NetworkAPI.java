package com.gluonhq.snl;

import io.privacyresearch.servermodel.CredentialsMessage;
import io.privacyresearch.servermodel.CredentialsResponseMessage;
import io.privacyresearch.servermodel.UserRemoteConfigListMessage;
import io.privacyresearch.servermodel.UserRemoteConfigMessage;
import io.privacyresearch.servermodel.PreKeyResponseItemMessage;
import io.privacyresearch.servermodel.PreKeyResponseMessage;
import io.privacyresearch.servermodel.PreKeyEntityMessage;
import io.privacyresearch.servermodel.SignedPreKeyEntityMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.api.groupsv2.CredentialResponse;
import org.whispersystems.signalservice.api.groupsv2.TemporalCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.push.PreKeyEntity;
import org.whispersystems.signalservice.internal.push.PreKeyResponse;
import org.whispersystems.signalservice.internal.push.PreKeyResponseItem;
import org.whispersystems.util.Base64;

/**
 *
 * @author johan
 */
public class NetworkAPI {

    public static Optional<CredentialsProvider> cp;

    static private NetworkClient networkClient;
    private static final Logger LOG = Logger.getLogger(NetworkAPI.class.getName());

    private static NetworkClient getClient() {
        if (networkClient == null) {
            networkClient = NetworkClient.createNetworkClient(cp);
        }
        return networkClient;
    }

    /**
     * Retrieve a sender certificate, required for unauthenticated messages
     * (sealed sender)
     *
     * @param cred
     * @return a byte array containing the certificate
     * @throws IOException
     */
    public static byte[] getSenderCertificate(CredentialsProvider cred) throws IOException {
        try {
            URI uri = new URI("xhttps://chat.signal.org/v1/certificate/delivery");
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", List.of(getAuthorizationHeader(cred)));
            Response response = getClient().sendRequest(uri, "GET", new byte[0], headers);
            byte[] raw = response.body().bytes();
            return raw;
        } catch (URISyntaxException ex) {
            Logger.getLogger(NetworkAPI.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }

    public static Map<String, Object> getRemoteConfig(CredentialsProvider cred) throws IOException {
        try {
            Map<String, Object> answer = new HashMap<>();
            URI uri = new URI("xhttps://chat.signal.org/v1/config");
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", List.of(getAuthorizationHeader(cred)));
            Response response = getClient().sendRequest(uri, "GET", new byte[0], headers);
            byte[] raw = response.body().bytes();
            UserRemoteConfigListMessage urlm = UserRemoteConfigListMessage.parseFrom(raw);
            for (UserRemoteConfigMessage urcm : urlm.getUserRemoteConfigList()) {
                answer.put(urcm.getName(), urcm.hasValue() ? urcm.getValue() : urcm.getEnabled());
            }
            return answer;
        } catch (URISyntaxException ex) {
            Logger.getLogger(NetworkAPI.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }

    public static PreKeyResponse getPreKey(String uuid, int deviceId) throws IOException {
        try {
            URI uri = new URI("xhttps://chat.signal.org/v2/keys/" + uuid + "/" + deviceId);
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", List.of(getAuthorizationHeader(cp.get())));
            Response response = getClient().sendRequest(uri, "GET", new byte[0], headers);
            byte[] raw = response.body().bytes();
            PreKeyResponseMessage pkrm = PreKeyResponseMessage.parseFrom(raw);
            byte[] keyBytes = pkrm.getIdentityKey().toByteArray();
            IdentityKey ik = new IdentityKey(keyBytes);
            List<PreKeyResponseItem> pkris = new ArrayList<>();
            for (PreKeyResponseItemMessage item : pkrm.getDevicesList()) {
                int devid = item.getDeviceId();
                int regid = item.getRegistrationId();
                PreKeyEntityMessage pke = item.getPreKeyEntity();
                long keyId = pke.getKeyId();
                if (keyId > Integer.MAX_VALUE) {
                    throw new RuntimeException("Major issue, can't cast a long to an int");
                }
                ECPublicKey pk = new ECPublicKey(pke.getPublicKeyBytes().toByteArray());
                PreKeyEntity preKey = new PreKeyEntity((int) keyId, pk);
                SignedPreKeyEntityMessage spke = item.getSignedPreKeyEntity();
                long skeyId = spke.getKeyId();
                if (skeyId > Integer.MAX_VALUE) {
                    throw new RuntimeException("Major issue, can't cast a long to an int");
                }
                ECPublicKey spk = new ECPublicKey(spke.getPublicKeyBytes().toByteArray());
                byte[] sig = spke.getSignature().toByteArray();
                SignedPreKeyEntity signedPreKey = new SignedPreKeyEntity((int) skeyId, spk, sig);
                PreKeyResponseItem pkItem = new PreKeyResponseItem(devid, regid, preKey, signedPreKey);
                pkris.add(pkItem);
            }
            PreKeyResponse answer = new PreKeyResponse(ik, pkris);
            return answer;
        } catch (URISyntaxException | InvalidKeyException ex) {
            Logger.getLogger(NetworkAPI.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }

    public static CredentialResponse retrieveGroupsV2Credentials(long todaySeconds)
            throws IOException {
        try {
            long todayPlus7 = todaySeconds + TimeUnit.DAYS.toSeconds(7);
            URI uri = new URI("xhttps://chat.signal.org/v1/certificate/auth/group?redemptionStartSeconds=" + todaySeconds + "&redemptionEndSeconds=" + todayPlus7);
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", List.of(getAuthorizationHeader(cp.get())));
            Response response = getClient().sendRequest(uri, "GET", new byte[0], headers);
            byte[] raw = response.body().bytes();
            CredentialsResponseMessage crm = CredentialsResponseMessage.parseFrom(raw);
            TemporalCredential[] creds = new TemporalCredential[crm.getCredentialsList().size()];
            int idx = 0;
            for (CredentialsMessage msg : crm.getCredentialsList()) {
                TemporalCredential tc = new TemporalCredential(msg.getCredentials().toByteArray(), msg.getRedemptionTime());
                creds[idx++] = tc;
            }
            CredentialResponse answer = new CredentialResponse(creds);
            LOG.info("Retrieved groupsv2Credentials");
            return answer;
        } catch (URISyntaxException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new IOException(ex);

        }
    }

    private static String getAuthorizationHeader(CredentialsProvider credentialsProvider) {
        try {
            String identifier = credentialsProvider.getAci() != null ? credentialsProvider.getAci().toString() : credentialsProvider.getE164();
            if (credentialsProvider.getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID) {
                identifier += "." + credentialsProvider.getDeviceId();
            }
            return "Basic " + Base64.encodeBytes((identifier + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
