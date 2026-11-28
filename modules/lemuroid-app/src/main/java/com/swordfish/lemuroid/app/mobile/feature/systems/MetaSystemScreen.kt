package com.swordfish.lemuroid.app.mobile.feature.systems

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGrid
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSystemCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSystemListRow
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidLayout
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidTextSize
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidViewMode
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo

@Composable
fun MetaSystemsScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: MetaSystemsViewModel,
) {
    val metaSystems = viewModel.availableMetaSystems.collectAsState(emptyList())
    MetaSystemsScreen(
        modifier = modifier,
        metaSystems = metaSystems.value,
        onSystemClicked = { navController.navigate("systems/${it.metaSystem.name}") },
    )
}

@Composable
private fun MetaSystemsScreen(
    modifier: Modifier = Modifier,
    metaSystems: List<MetaSystemInfo>,
    onSystemClicked: (MetaSystemInfo) -> Unit,
) {
    if (metaSystems.isEmpty()) {
        LemuroidEmptyView()
        return
    }

    val layout = LemuroidViewMode.SYSTEMS.rememberLayout().value
    if (layout == LemuroidLayout.LIST) {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = LemuroidGrid.listPadding) {
            items(metaSystems.size, key = { metaSystems[it].metaSystem }) { index ->
                val system = metaSystems[index]
                LemuroidSystemListRow(
                    modifier = Modifier.animateItem(),
                    system = system,
                    onClick = { onSystemClicked(system) },
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        contentPadding = LemuroidGrid.contentPadding,
        columns = LemuroidGrid.columns(small = layout == LemuroidLayout.TILES_SMALL),
        horizontalArrangement = LemuroidGrid.arrangement,
        verticalArrangement = LemuroidGrid.arrangement,
    ) {
        items(metaSystems.size, key = { metaSystems[it].metaSystem }) { index ->
            val system = metaSystems[index]
            LemuroidSystemCard(
                modifier = Modifier.animateItem(),
                system = system,
                textSize = if (layout == LemuroidLayout.TILES_SMALL) LemuroidTextSize.TILE_SMALL else LemuroidTextSize.TILE,
                onClick = { onSystemClicked(system) },
            )
        }
    }
}
