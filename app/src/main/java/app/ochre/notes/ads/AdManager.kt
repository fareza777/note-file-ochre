package app.ochre.notes.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdManager(context: Context) {

    private val appContext = context.applicationContext
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var editorExits = 0
    private var lastInterstitialAt = 0L

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    init {
        MobileAds.initialize(appContext)
        loadInterstitial()
        loadRewarded()
    }

    fun loadInterstitial() {
        if (interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            appContext,
            AdConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                            loadInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitial = null
                            loadInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    interstitial = null
                }
            },
        )
    }

    fun onEditorClosed(activity: Activity, adsFree: Boolean) {
        if (adsFree) return
        editorExits++
        if (editorExits < EXITS_BEFORE_INTERSTITIAL) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterstitialAt < INTERSTITIAL_INTERVAL_MS) return
        val ad = interstitial
        if (ad == null) {
            loadInterstitial()
            return
        }
        lastInterstitialAt = now
        editorExits = 0
        interstitial = null
        ad.show(activity)
    }

    fun loadRewarded() {
        if (rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(
            appContext,
            AdConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingRewarded = false
                    rewarded = ad
                    _rewardedReady.value = true
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewarded = null
                            _rewardedReady.value = false
                            loadRewarded()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            rewarded = null
                            _rewardedReady.value = false
                            loadRewarded()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                    rewarded = null
                    _rewardedReady.value = false
                }
            },
        )
    }

    fun showRewarded(activity: Activity, onEarned: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            loadRewarded()
            onUnavailable()
            return
        }
        rewarded = null
        _rewardedReady.value = false
        ad.show(activity) { onEarned() }
    }

    private companion object {
        const val EXITS_BEFORE_INTERSTITIAL = 3
        const val INTERSTITIAL_INTERVAL_MS = 90_000L
    }
}
