package cz.kisiala.pocasi.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import cz.kisiala.pocasi.BuildConfig
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.ui.today.WeatherCard

/** Odkaz v textu O aplikaci. */
private class Link(@StringRes val label: Int, val url: String)

/** Zdroj dat: co poskytuje, a povinná atribuce s odkazy (licence CC BY 4.0 / ODbL). */
private class DataSource(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val attribution: Int,
    val links: List<Link>,
)

private val DATA_SOURCES = listOf(
    DataSource(
        title = R.string.about_forecast_title,
        description = R.string.about_forecast_text,
        attribution = R.string.about_forecast_attribution,
        links = listOf(
            Link(R.string.about_link_open_meteo, "https://open-meteo.com/"),
            Link(R.string.about_link_cc_by, "https://creativecommons.org/licenses/by/4.0/deed.cs"),
        ),
    ),
    DataSource(
        title = R.string.about_radar_title,
        description = R.string.about_radar_text,
        attribution = R.string.about_radar_attribution,
        links = listOf(
            Link(R.string.about_link_chmi_opendata, "https://opendata.chmi.cz/"),
            Link(R.string.about_link_cc_by, "https://creativecommons.org/licenses/by/4.0/deed.cs"),
        ),
    ),
    DataSource(
        title = R.string.about_map_title,
        description = R.string.about_map_text,
        attribution = R.string.about_map_attribution,
        links = listOf(
            Link(R.string.about_link_openfreemap, "https://openfreemap.org/"),
            Link(R.string.about_link_openmaptiles, "https://www.openmaptiles.org/"),
            Link(R.string.about_link_osm_copyright, "https://www.openstreetmap.org/copyright"),
        ),
    ),
)

/** Celoobrazovková stránka O aplikaci (dialog přes celou obrazovku, zavírá se šipkou i systémovým Zpět). */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        AboutContent(onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutContent(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_about)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeader()
            SectionTitle(R.string.about_author_title)
            AuthorCard()
            SectionTitle(R.string.about_sources_title)
            DATA_SOURCES.forEach { DataSourceCard(it) }
            Text(
                stringResource(R.string.about_changes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SectionTitle(R.string.about_libraries_title)
            WeatherCard {
                Text(
                    stringResource(R.string.about_libraries_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            // Popředí adaptivní ikony má bezpečnou zónu 66/108 – zvětšíme, ať vyplní kruh jako na ploše.
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f),
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.about_version_full, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthorCard() {
    val context = LocalContext.current
    val subject = stringResource(R.string.about_contact_subject)
    WeatherCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.about_author_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.about_contact_text), style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = {
                    // Gmail čte předmět jen z mailto URI, jiní klienti (Outlook) z EXTRA_SUBJECT – dáme obojí.
                    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$AUTHOR_EMAIL?subject=${Uri.encode(subject)}".toUri())
                        .putExtra(Intent.EXTRA_SUBJECT, subject)
                    // Bez e-mailového klienta se nic nestane (místo pádu).
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                modifier = Modifier.offset(x = (-12).dp),
            ) {
                Icon(
                    Icons.Outlined.Email,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                )
                Text(AUTHOR_EMAIL)
            }
        }
    }
}

private const val AUTHOR_EMAIL = "o.kisiala@gmail.com"

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun DataSourceCard(source: DataSource) {
    WeatherCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(source.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(source.description), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                stringResource(source.attribution),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinksRow(source.links)
        }
    }
}

/** Odkazy jako textová tlačítka (dotyková plocha 48 dp); klepnutí otevře prohlížeč. */
@Composable
private fun LinksRow(links: List<Link>) {
    val uriHandler = LocalUriHandler.current
    FlowRow(Modifier.offset(x = (-12).dp)) {
        links.forEach { link ->
            TextButton(onClick = { uriHandler.openUri(link.url) }) {
                Text(stringResource(link.label))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(16.dp),
                )
            }
        }
    }
}
