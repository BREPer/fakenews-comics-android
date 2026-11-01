package com.fakenews.comics.ui

import androidx.lifecycle.ViewModel
import com.fakenews.comics.data.session.SessionBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(sessionBus: SessionBus) : ViewModel() {
    val unauthorized: SharedFlow<Unit> = sessionBus.unauthorized
}
