package com.lavidanoesunbanano.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.navigation.StartResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(startResolver: StartResolver) : ViewModel() {

    val startDestination: StateFlow<Any?> = flow { emit(startResolver.start()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
