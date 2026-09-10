package dev.dhuelin.watchguru.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.AvailabilityResponse
import dev.dhuelin.watchguru.data.WatchOffers

/**
 * Where the user can actually watch this, in their own country.
 *
 * The section is absent rather than empty when there are no offers: an empty
 * list means either "on no service here" or "we have not been able to ask the
 * provider", and the two are indistinguishable from the response, so claiming
 * the first would sometimes be a lie.
 *
 * The country is the one on the user's profile -- the backend resolves it from
 * the token -- which the caption says, because offers for the wrong country are
 * worse than none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhereToWatch(
    offers: List<AvailabilityResponse>,
    modifier: Modifier = Modifier,
) {
    if (offers.isEmpty()) return

    val groups = remember(offers) { WatchOffers.group(offers) }
    val link = remember(offers) { WatchOffers.link(offers) }
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.where_to_watch),
            style = MaterialTheme.typography.titleMedium,
        )

        groups.forEach { group ->
            Text(
                text = stringResource(group.offerType.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.services.forEach { ServiceChip(it) }
            }
        }

        if (link != null) {
            TextButton(
                onClick = { uriHandler.openUri(link) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_watch_options))
            }
        }

        Text(
            text = stringResource(R.string.where_to_watch_region),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Required alongside TMDB's own attribution: the availability data is
        // JustWatch's, and TMDB's terms say so.
        Text(
            text = stringResource(R.string.justwatch_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One service, named as well as pictured.
 *
 * Not tappable: the provider has no per-service deep link, so a chip that
 * looked like a button would open a web page for every service rather than the
 * one pressed. The link below the groups is the honest version of that.
 */
@Composable
private fun ServiceChip(offer: AvailabilityResponse) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                // One node for the pair, so TalkBack says "Netflix" rather
                // than stopping on a decorative image first.
                .semantics(mergeDescendants = true) {
                    contentDescription = offer.serviceName
                },
        ) {
            ServiceLogo(url = offer.logoUrl, name = offer.serviceName)
            Text(
                text = offer.serviceName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** The service's logo, or its initial where there isn't one. */
@Composable
private fun ServiceLogo(url: String?, name: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                // The name is right beside it; describing the logo too would
                // make TalkBack read the service twice.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@StringRes
private fun AvailabilityResponse.OfferType.labelRes(): Int = when (this) {
    AvailabilityResponse.OfferType.FLATRATE -> R.string.offer_flatrate
    AvailabilityResponse.OfferType.FREE -> R.string.offer_free
    AvailabilityResponse.OfferType.ADS -> R.string.offer_ads
    AvailabilityResponse.OfferType.RENT -> R.string.offer_rent
    AvailabilityResponse.OfferType.BUY -> R.string.offer_buy
}
