package com.learningmachine.android.app.data.cert.v11;

import com.learningmachine.android.app.data.cert.BlockCert;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;

import java.security.SignatureException;

import timber.log.Timber;

/**
 * Created by bolot on 5/24/17.
 */

public class BlockCertV11 extends CertificateSchemaV11 implements BlockCert {
    @Override
    public String getCertUid() {
        if (getAssertion() == null) {
            return null;
        }
        return getAssertion().getUid();
    }

    @Override
    public String getCertName() {
        return getCertificate().getTitle();
    }

    @Override
    public String getCertDescription() {
        return getCertificate().getDescription();
    }

    @Override
    public String getIssuerId() {
        return null;
    }

    @Override
    public String getIssueDate() {
        return getAssertion().getIssuedOn().toString();
    }

    @Override
    public String getUrl() {
        return getAssertion().getId().toString();
    }

    @Override
    public String getRecipientPublicKey() {
        return getRecipient().getPubkey();
    }

    @Override
    public String getSourceId() {
        return null;
    }

    @Override
    public String getMerkleRoot() {
        return null;
    }

    @Override
    public String getAddress(NetworkParameters networkParameters) {
        try {
            ECKey ecKey = ECKey.signedMessageToKey(getCertUid(), getSignature());
            Address address = ecKey.toAddress(networkParameters);
            return address.toBase58();
        } catch (SignatureException e) {
            Timber.e(e, "The document signature is invalid");
            return null;
        }
    }
}