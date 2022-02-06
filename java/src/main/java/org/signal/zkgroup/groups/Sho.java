/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.signal.zkgroup.groups;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author johan
 */
public class Sho {

    static final int HASH_LEN = 32;

    byte[] cv = new byte[HASH_LEN];
    Mac mac;

    public Sho(byte[] label) {
        absorb(label);
        ratchet();
    }

    public void absorb(byte[] input) {
        try {
            this.mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.cv, "HmacSHA256"));
            mac.update(input);
        } catch (InvalidKeyException ex) {
            ex.printStackTrace();
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }
    }

    public void ratchet() {
        this.mac.update((byte) 0);
        this.cv = this.mac.doFinal();
        this.mac.reset();
    }

    public byte[] squeeze(int outlen) {
        try {
            ByteArrayOutputStream results = new ByteArrayOutputStream();
            Mac outputHasherPrefix = Mac.getInstance("HmacSHA256");
            outputHasherPrefix.init(new SecretKeySpec(this.cv, "HmacSHA256"));
            System.err.println("in squeeze, inited with cv[0] = " + this.cv[0]);
            int i = 0;
            while (i * HASH_LEN < outlen) {
                Mac workMac = outputHasherPrefix;

                workMac.update(intToByte64(i));
                workMac.update((byte) 1);
                byte[] part = workMac.doFinal();
                System.err.println("in squeeze, part0 = " + part[0] + ", " + part[1] + ", " + part[2]);
                results.writeBytes(part);
                i += 1;
            }
            return results.toByteArray();
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        } catch (InvalidKeyException ex) {
            ex.printStackTrace();

        }
        return null;
    }

    private byte[] intToByte64(int src) {
        int rest = src;
        byte[] answer = new byte[8];
        for (int i = 7; i >= 0; i--) {
            answer[i] = (byte) (rest % 256);
            rest = rest / 256;
        }
        return answer;
    }

    private byte[] expand(int outputSize) {
        try {
            byte[] prk = cv;
            int iterations = (int) Math.ceil((double) outputSize / (double) HASH_LEN);
            byte[] mixin = new byte[0];
            ByteArrayOutputStream results = new ByteArrayOutputStream();
            int remainingBytes = outputSize;

            for (int i = 1; i < iterations + 1; i++) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));

                mac.update(mixin);

                mac.update((byte) i);

                byte[] stepResult = mac.doFinal();
                int stepSize = Math.min(remainingBytes, stepResult.length);

                results.write(stepResult, 0, stepSize);

                mixin = stepResult;
                remainingBytes -= stepSize;
            }

            return results.toByteArray();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public byte[] getcv() {
        return this.cv;
    }

}
