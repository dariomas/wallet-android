package com.learningmachine.android.app.data.bitcoin;

import android.content.Context;
import android.support.annotation.VisibleForTesting;

import com.learningmachine.android.app.LMConstants;
import com.learningmachine.android.app.R;
import com.learningmachine.android.app.data.error.ExceptionWithResourceString;
import com.learningmachine.android.app.data.store.CertificateStore;
import com.learningmachine.android.app.data.store.IssuerStore;
import com.learningmachine.android.app.util.BitcoinUtils;
import com.learningmachine.android.app.util.StringUtils;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;
import org.bitcoinj.wallet.WalletProtobufSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

import rx.Observable;
import timber.log.Timber;

public class BitcoinManager {

    private static final String PASSPHRASE_DELIMETER = " ";

    private final Context mContext;
    private final NetworkParameters mNetworkParameters;
    private final IssuerStore mIssuerStore;
    private final CertificateStore mCertificateStore;
    private Wallet mWallet;

    public BitcoinManager(Context context, NetworkParameters networkParameters, IssuerStore issuerStore, CertificateStore certificateStore) {
        mContext = context;
        mNetworkParameters = networkParameters;
        mIssuerStore = issuerStore;
        mCertificateStore = certificateStore;
    }

    private Observable<Wallet> getWallet() {
        return Observable.defer(() -> {
            if (mWallet != null) {
                return Observable.just(mWallet);
            }
            if (getWalletFile().exists()) {
                return loadWallet();
            } else {
                return createWallet();
            }
        });
    }

    @VisibleForTesting
    protected File getWalletFile() {
        return new File(mContext.getFilesDir(), LMConstants.WALLET_FILE);
    }

    private Observable<Wallet> createWallet() {
        SecureRandom random = new SecureRandom();
        byte[] seedData = random.generateSeed(LMConstants.WALLET_SEED_BYTE_SIZE);
        buildWallet(seedData);
        return Observable.just(mWallet);
    }

    private Observable<Wallet> buildWallet(byte[] seedData) {
        mWallet = BitcoinUtils.createWallet(mNetworkParameters, seedData);
        return saveWallet();
    }

    private Observable<Wallet> buildWallet(String seedPhrase) {
        mWallet = BitcoinUtils.createWallet(mNetworkParameters, seedPhrase);
        return saveWallet();
    }

    /**
     * @return true if wallet was loaded successfully
     */
    private Observable<Wallet> loadWallet() {
        try (FileInputStream walletStream = new FileInputStream(getWalletFile())) {
            WalletExtension[] extensions = {};
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            WalletProtobufSerializer serializer = new WalletProtobufSerializer();
            mWallet = serializer.readWallet(mNetworkParameters, extensions, proto);
            Timber.d("Wallet successfully loaded");
            return Observable.just(mWallet);
        } catch (UnreadableWalletException e) {
            Timber.e(e, "Wallet is corrupted");
            return Observable.error(e);
        } catch (FileNotFoundException e) {
            Timber.e(e, "Wallet file not found");
            return Observable.error(e);
        } catch (IOException e) {
            Timber.e(e, "Wallet unable to be parsed");
            return Observable.error(e);
        }
    }

    /**
     * @return true if wallet was saved successfully
     */
    private Observable<Wallet> saveWallet() {
        if (mWallet == null) {
            Exception e = new Exception("Wallet doesn't exist");
            Timber.e(e, "Wallet doesn't exist");
            return Observable.error(e);
        }
        try {
            mWallet.saveToFile(getWalletFile());
            Timber.d("Wallet successfully saved");
            return Observable.just(mWallet);
        } catch (IOException e) {
            Timber.e(e, "Unable to save Wallet");
            return Observable.error(e);
        }
    }

    public Observable<String> getPassphrase() {
        return getWallet().map(wallet -> {
            DeterministicSeed seed = wallet.getKeyChainSeed();
            List<String> mnemonicCode = seed.getMnemonicCode();
            return StringUtils.join(PASSPHRASE_DELIMETER, mnemonicCode);
        });
    }

    public Observable<Wallet> setPassphrase(String newPassphrase) {
        if (StringUtils.isEmpty(newPassphrase)) {
            return Observable.error(new ExceptionWithResourceString(R.string.error_invalid_passphrase_empty));
        }
        if (!BitcoinUtils.isValidPassphrase(newPassphrase)) {
            return Observable.error(new ExceptionWithResourceString(R.string.error_invalid_passphrase_malformed));
        }
        mIssuerStore.reset();
        mCertificateStore.reset();
        return buildWallet(newPassphrase);
    }

    public Observable<String> getBitcoinAddress() {
        return getWallet().map(wallet -> wallet.currentReceiveAddress()
                .toString());
    }
}
