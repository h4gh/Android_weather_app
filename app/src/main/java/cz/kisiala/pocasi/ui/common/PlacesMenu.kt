package cz.kisiala.pocasi.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.Place

/** Přepínání míst: aktuální poloha, oblíbená, hledané místo; pod nimi přidání a úprava oblíbených. */
@Composable
fun PlacesMenu(
    expanded: Boolean,
    selected: Place?,
    places: List<Place>,
    onSelect: (Place) -> Unit,
    onDismiss: () -> Unit,
    actions: AppMenuActions,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        places.forEach { place ->
            DropdownMenuItem(
                text = {
                    Text(
                        if (place.isCurrentLocation) {
                            "${place.name} · ${stringResource(R.string.place_my_location)}"
                        } else {
                            place.name
                        },
                    )
                },
                leadingIcon = {
                    Icon(
                        when (place.kind) {
                            Place.Kind.CurrentLocation -> Icons.Outlined.MyLocation
                            Place.Kind.Saved -> Icons.Filled.Star
                            else -> Icons.Outlined.Place
                        },
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (place.id == selected?.id) Icon(Icons.Outlined.Check, contentDescription = null)
                },
                onClick = { onSelect(place) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.places_add)) },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onSearchPlace()
            },
        )
        if (places.any { it.kind == Place.Kind.Saved }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.places_edit_favorites)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.onEditFavorites()
                },
            )
        }
    }
}
