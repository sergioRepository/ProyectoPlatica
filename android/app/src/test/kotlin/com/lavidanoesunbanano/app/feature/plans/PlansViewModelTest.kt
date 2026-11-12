package com.lavidanoesunbanano.app.feature.plans

import android.app.Activity
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavidanoesunbanano.app.data.FakeAgentSchedule
import com.lavidanoesunbanano.app.data.FakeDeviceStore
import com.lavidanoesunbanano.app.data.FakeSettingsStore
import com.lavidanoesunbanano.app.data.billing.BillingState
import com.lavidanoesunbanano.app.data.billing.FakeBillingRepository
import com.lavidanoesunbanano.app.data.billing.StorePrice
import com.lavidanoesunbanano.app.data.billing.StoreProduct
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.di.NetworkModule
import com.lavidanoesunbanano.app.domain.billing.PlanTier
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import retrofit2.create

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class PlansViewModelTest {

    private val prices = mapOf(
        StoreProduct.MONTHLY to StorePrice("US$ 1,99", 1_990_000, "USD", "P1M"),
        StoreProduct.SEMESTER to StorePrice("US$ 8,39", 8_390_000, "USD", "P6M"),
    )
    private val billing = FakeBillingRepository(BillingState(loading = false, prices = prices))
    private val activity: Activity = Robolectric.buildActivity(ComponentActivity::class.java).get()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(validatedPlan: String?): PlansViewModel {
        val store = FakeDeviceStore().apply { this.validatedPlan = validatedPlan }
        val config = BackendConfig("http://localhost/")
        val retrofit = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config)
        // Sin token, status() devuelve null sin tocar la red.
        val devices = DeviceRepository(store, retrofit.create(), config, FakeSettingsStore(), FakeAgentSchedule())
        return PlansViewModel(billing, devices, store, SavedStateHandle())
    }

    @Test
    fun `opens on the paid plan or on monthly`() {
        assertEquals(PlanTier.SEMESTER, viewModel("semester").state.value.selected)
        assertEquals(PlanTier.MONTHLY, viewModel(null).state.value.selected)
    }

    @Test
    fun `selecting a card never changes the validated plan`() {
        val vm = viewModel("monthly")
        vm.select(PlanTier.SEMESTER)
        vm.select(PlanTier.FREE)
        assertEquals(PlanTier.MONTHLY, vm.state.value.validated)
        assertEquals(PlanTier.FREE, vm.state.value.selected)
    }

    @Test
    fun `a second tap while busy does not buy again`() {
        val vm = viewModel(null)
        vm.onPrimary(activity)
        vm.onPrimary(activity)
        assertEquals(listOf(StoreProduct.MONTHLY), billing.purchased)
    }

    @Test
    fun `the current plan does not buy`() {
        val vm = viewModel("monthly")
        vm.onPrimary(activity)
        assertEquals(emptyList(), billing.purchased)
    }
}
