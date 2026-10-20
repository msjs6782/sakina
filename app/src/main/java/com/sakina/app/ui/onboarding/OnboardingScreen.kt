package com.sakina.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.AsrMethod
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.data.AppSettings
import com.sakina.app.data.VisualStyle
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.PlacePurpose
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.ChoiceRow
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.home.HeroSurface
import com.sakina.app.ui.settings.Segmented
import com.sakina.app.ui.theme.Amiri
import com.sakina.app.ui.theme.LocalExtras
import com.sakina.app.ui.theme.SkyPeriod

private const val STEPS = 4

@Composable
fun OnboardingScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish = { vm.update { it.copy(onboardingDone = true) } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str.stepOf(LocalFmt.current.num(step + 1), LocalFmt.current.num(STEPS)), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (step < STEPS - 1) TextButton(onClick = finish) { Text(str.skipAll) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> WelcomeStep(vm, s)
                1 -> LocationStep(vm, s)
                2 -> MethodStep(vm, s)
                else -> ReadyStep(s)
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(STEPS) { i ->
                    Box(
                        Modifier.size(width = if (i == step) 22.dp else 8.dp, height = 8.dp).clip(CircleShape)
                            .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            if (step in 1 until STEPS - 1) {
                TextButton(onClick = { step++ }) { Text(str.skip) }
                Spacer(Modifier.width(8.dp))
            }
            if (step < STEPS - 1) {
                Button(onClick = { step++ }) { Text(str.next) }
            } else {
                Button(onClick = finish) { Text(str.start) }
            }
        }
    }
}

@Composable
private fun WelcomeStep(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val ex = LocalExtras.current
    if (ex.art) {
        // The welcome painting: the town at golden hour under a tall sky, the words over the sky.
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(340.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp)).testTag("welcome_art"),
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.sakina.app.R.drawable.art_welcome), null,
                Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = androidx.compose.ui.Alignment.BottomCenter,
            )
            androidx.compose.foundation.layout.Box(
                Modifier.matchParentSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), 0.55f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f),
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                    ),
                ),
            )
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(str.appName, style = MaterialTheme.typography.displaySmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.height(8.dp))
                Text(str.welcomeTitle, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.height(6.dp))
                Text(str.welcomeBody, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
            }
        }
    } else HeroSurface(SkyPeriod.FAJR, Modifier.fillMaxWidth().padding(top = 8.dp), corner = 32.dp) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(str.appName, style = MaterialTheme.typography.displaySmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = ex.heroOn)
            Spacer(Modifier.height(8.dp))
            Text(str.welcomeTitle, style = MaterialTheme.typography.titleLarge, color = ex.heroOn)
            Spacer(Modifier.height(6.dp))
            Text(str.welcomeBody, style = MaterialTheme.typography.bodyMedium, color = ex.heroOnMuted)
        }
    }
    SectionCard(title = str.language, icon = SakinaIcons.Globe) {
        Segmented(listOf("العربية", "English"), if (s.language == Lang.AR) 0 else 1) { i ->
            val lang = if (i == 0) Lang.AR else Lang.EN
            vm.update { it.copy(language = lang, arabicDigits = lang == Lang.AR) }
        }
    }
    SectionCard(title = str.styleTitle, icon = SakinaIcons.Palette) {
        Segmented(VisualStyle.entries.map { str.styleName(it) }, VisualStyle.entries.indexOf(s.style)) { i ->
            vm.update { it.copy(style = VisualStyle.entries[i]) }
        }
        Spacer(Modifier.height(6.dp))
        NoteText(str.styleDetail(s.style))
    }
}

@Composable
private fun LocationStep(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    Text(str.stepLocation, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
    Text(str.chooseLocationBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    s.activeLocation?.let { loc ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SakinaIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(loc.name(str.lang), style = MaterialTheme.typography.titleMedium)
                    Text(loc.zone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Button(onClick = { vm.push(Route.CityPicker(PlacePurpose.ADD)) }, modifier = Modifier.fillMaxWidth()) {
        Icon(SakinaIcons.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str.pickCity)
    }
    OutlinedButton(onClick = { vm.push(Route.Gps(PlacePurpose.ADD)) }, modifier = Modifier.fillMaxWidth()) {
        Icon(SakinaIcons.Gps, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str.useGps)
    }
    OutlinedButton(onClick = { vm.push(Route.Manual(PlacePurpose.ADD)) }, modifier = Modifier.fillMaxWidth()) {
        Icon(SakinaIcons.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str.manualEntry)
    }
    NoteText(str.locationStepNote)
}

@Composable
private fun MethodStep(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    Text(str.stepMethod, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
    NoteText(str.methodChoiceNote)
    SectionCard {
        CalcMethod.entries.forEach { m ->
            ChoiceRow(s.method == m, str.methodName(m), str.methodDetail(m), onClick = { vm.update { it.copy(method = m) } })
        }
    }
    SectionCard(title = str.asrTitle, icon = SakinaIcons.Asr) {
        AsrMethod.entries.forEach { a ->
            ChoiceRow(s.asr == a, str.asrName(a), onClick = { vm.update { it.copy(asr = a) } })
        }
    }
}

@Composable
private fun ReadyStep(s: AppSettings) {
    val str = LocalStrings.current
    Spacer(Modifier.height(24.dp))
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(SakinaIcons.Check, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    Text(str.readyTitle, style = MaterialTheme.typography.headlineMedium)
    SectionCard {
        Text(s.activeLocation?.name(str.lang) ?: str.chooseLocationTitle, style = MaterialTheme.typography.titleMedium)
        Text(str.methodName(s.method) + " · " + str.asrName(s.asr), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    NoteText(str.readyBody)
}
