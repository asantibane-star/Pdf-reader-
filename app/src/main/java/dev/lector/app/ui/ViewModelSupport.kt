package dev.lector.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.lector.app.LectorApp
import dev.lector.app.di.AppServices

/** Contenedor de dependencias a partir de los extras de creación de un ViewModel. */
fun CreationExtras.services(): AppServices {
    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) { "Sin Application" }
    return (app as LectorApp).container
}
