package com.vachan.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.EducationLevel
import com.vachan.app.core.model.FamilyType
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.Habit
import com.vachan.app.core.model.IncomeBand
import com.vachan.app.core.model.ManagedBy
import com.vachan.app.core.model.MaritalStatus
import com.vachan.app.core.model.PartnerPreferences
import com.vachan.app.core.model.Photo
import com.vachan.app.core.model.PhotoVisibility
import com.vachan.app.core.model.PreferenceKey
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.Religion
import com.vachan.app.core.model.ValueAnswer
import com.vachan.app.core.model.ValueQuestion
import com.vachan.app.core.model.heightLabel
import com.vachan.app.core.model.minimumMarriageAge
import com.vachan.app.core.photos.PhotoProcessor
import com.vachan.app.core.safety.PiiDetector
import com.vachan.app.data.AiRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.data.demo.DemoSeed
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.ChoiceChips
import com.vachan.app.ui.components.CircleIconButton
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.FieldLabel
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.MultiChips
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.RadioRow
import com.vachan.app.ui.components.SecondaryButton
import com.vachan.app.ui.components.Section
import com.vachan.app.ui.components.SegmentedTabs
import com.vachan.app.ui.components.StepProgress
import com.vachan.app.ui.components.ToggleRow
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private val emptyProfile = Profile(
    id = "", firstName = "", lastInitial = "", gender = Gender.FEMALE, age = 0, heightCm = 165,
    location = DemoSeed.Cities.mumbai, motherTongue = "Hindi", education = EducationLevel.BACHELORS,
    profession = "", diet = Diet.VEGETARIAN, languages = setOf("English", "Hindi"),
    preferences = PartnerPreferences(minAge = 25, maxAge = 33),
)

/** A processed (metadata-free) photo waiting for the profile to exist before it can be saved. */
data class PendingPhoto(val id: String, val jpeg: ByteArray) {
    override fun equals(other: Any?) = other is PendingPhoto && other.id == id
    override fun hashCode() = id.hashCode()
}

data class EditorUi(
    val draft: Profile = emptyProfile,
    val step: Int = 0,
    val dobLabel: String = "",
    val saving: Boolean = false,
    val aiBusy: Boolean = false,
    val photoBusy: Boolean = false,
    val pendingPhotos: List<PendingPhoto> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false,
    val aiEnabled: Boolean = false,
)

@HiltViewModel
class ProfileEditorViewModel @javax.inject.Inject constructor(
    private val profiles: ProfileRepository,
    private val ai: AiRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditorUi(draft = profiles.myProfile.value ?: emptyProfile, aiEnabled = ai.aiEnabled.value))
    val ui = _ui.asStateFlow()
    val isNew: Boolean get() = profiles.myProfile.value == null

    fun update(transform: (Profile) -> Profile) { _ui.value = _ui.value.copy(draft = transform(_ui.value.draft), error = null) }
    fun prefs(transform: (PartnerPreferences) -> PartnerPreferences) = update { it.copy(preferences = transform(it.preferences)) }

    /** Date of birth from the picker (UTC midnight). Only the age is stored and shown. */
    fun onDob(millis: Long) {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        val age = Period.between(date, LocalDate.now()).years
        _ui.value = _ui.value.copy(dobLabel = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())))
        update { it.copy(age = age) }
    }

    fun goTo(step: Int) {
        if (step > _ui.value.step) validate(_ui.value.step)?.let { _ui.value = _ui.value.copy(error = it); return }
        _ui.value = _ui.value.copy(step = step.coerceIn(0, STEPS.lastIndex), error = null)
    }

    private fun validate(step: Int): String? {
        val d = _ui.value.draft
        return when (step) {
            0 -> when {
                d.firstName.isBlank() -> "Please enter a first name"
                d.age == 0 -> "Please choose your date of birth"
                d.age < d.gender.minimumMarriageAge() -> "Members must be at least ${d.gender.minimumMarriageAge()} (legal marriageable age)"
                d.age > 80 -> "Please check the date of birth"
                else -> null
            }
            2 -> if (d.profession.isBlank()) "Please add your profession" else null
            4 -> if (d.values.size < 8) "Please answer at least 8 questions — they're the heart of good matching (${d.values.size}/8)" else null
            6 -> when {
                d.about.length < 40 -> "Write at least a couple of sentences about yourself (or tap \"Draft my bio\")"
                PiiDetector.containsContactDetails(d.about) -> "Please remove contact details from your bio. You can share your number privately after you match."
                else -> null
            }
            else -> null
        }
    }

    fun draftBio(tone: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(aiBusy = true)
        val bio = runCatching { ai.draftBio(_ui.value.draft, tone, settings.settings.value.language) }.getOrNull()
        if (bio != null) update { it.copy(about = bio.take(1200)) } else _ui.value = _ui.value.copy(error = "Couldn't draft a bio just now. Please try again.")
        _ui.value = _ui.value.copy(aiBusy = false)
    }

    val photoCount: Int get() = _ui.value.draft.photos.size + _ui.value.pendingPhotos.size

    /** Decodes, strips metadata and either uploads (existing profile) or holds the photo until save. */
    fun addPhoto(context: Context, uri: Uri) = viewModelScope.launch {
        if (photoCount >= MAX_PHOTOS) { _ui.value = _ui.value.copy(error = "You can add up to $MAX_PHOTOS photos"); return@launch }
        _ui.value = _ui.value.copy(photoBusy = true, error = null)
        runCatching { PhotoProcessor.process(context, uri) }
            .onSuccess { jpeg ->
                if (isNew) {
                    _ui.value = _ui.value.copy(pendingPhotos = _ui.value.pendingPhotos + PendingPhoto(UUID.randomUUID().toString(), jpeg))
                } else {
                    profiles.addPhoto(jpeg)
                        .onSuccess { photo -> update { it.copy(photos = it.photos + photo) } }
                        .onFailure { _ui.value = _ui.value.copy(error = it.userMessage("Couldn't add that photo. Please try again.")) }
                }
            }
            .onFailure { _ui.value = _ui.value.copy(error = it.userMessage("We couldn't use that photo. Please choose another.")) }
        _ui.value = _ui.value.copy(photoBusy = false)
    }

    fun removePhoto(id: String) = viewModelScope.launch {
        if (_ui.value.pendingPhotos.any { it.id == id }) {
            _ui.value = _ui.value.copy(pendingPhotos = _ui.value.pendingPhotos.filterNot { it.id == id })
            return@launch
        }
        profiles.removePhoto(id)
            .onSuccess { update { p -> p.copy(photos = p.photos.filterNot { it.id == id }) } }
            .onFailure { _ui.value = _ui.value.copy(error = it.userMessage()) }
    }

    fun makePrimary(id: String) = viewModelScope.launch {
        profiles.setPrimaryPhoto(id).onSuccess {
            update { p -> p.photos.firstOrNull { it.id == id }?.let { t -> p.copy(photos = listOf(t) + p.photos.filterNot { it.id == id }) } ?: p }
        }
    }

    fun save() = viewModelScope.launch {
        validate(_ui.value.step)?.let { _ui.value = _ui.value.copy(error = it); return@launch }
        _ui.value = _ui.value.copy(saving = true, error = null)
        profiles.saveMyProfile(_ui.value.draft)
            .onSuccess {
                // Photos picked before the profile existed are uploaded now, in order.
                val pending = _ui.value.pendingPhotos
                var failed = 0
                pending.forEach { pp -> profiles.addPhoto(pp.jpeg).onFailure { failed++ } }
                _ui.value = _ui.value.copy(
                    saving = false, saved = true, pendingPhotos = emptyList(),
                    error = if (failed > 0) "$failed photo(s) couldn't be uploaded. Add them again from your profile." else null,
                )
            }
            .onFailure { _ui.value = _ui.value.copy(saving = false, error = it.userMessage("Couldn't save your profile. Please try again.")) }
    }

    companion object {
        const val MAX_PHOTOS = 6
        val STEPS = listOf("Basics", "Background", "Career", "Lifestyle", "Values Compass", "Partner preferences", "Photos and bio")
        private val TITLES = listOf(
            "First, the basics", "Where you're from", "Study and work", "Everyday life",
            "What matters most", "Who you hope to meet", "Show who you are",
        )
        fun title(step: Int) = TITLES[step]
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(isOnboarding: Boolean, onDone: () -> Unit, vm: ProfileEditorViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.saved) { if (ui.saved) onDone() }
    val steps = ProfileEditorViewModel.STEPS
    val scroll = rememberScrollState()
    LaunchedEffect(ui.step) { scroll.scrollTo(0) }
    BackHandler(enabled = ui.step > 0) { vm.goTo(ui.step - 1) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Step ${ui.step + 1} of ${steps.size}",
                onBack = when {
                    ui.step > 0 -> ({ vm.goTo(ui.step - 1) })
                    !isOnboarding -> onDone
                    else -> null
                },
                backIcon = if (ui.step == 0) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                backDescription = if (ui.step == 0) "Close" else "Previous step",
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.error?.let { Text(it, color = AanganColors.extended.danger, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isOnboarding && ui.step < steps.lastIndex) SecondaryButton("Save", vm::save, Modifier.weight(1f), enabled = !ui.saving)
                    PrimaryButton(
                        if (ui.step == steps.lastIndex) (if (isOnboarding) "Finish" else "Save") else "Continue",
                        { if (ui.step == steps.lastIndex) vm.save() else vm.goTo(ui.step + 1) },
                        Modifier.weight(1f), loading = ui.saving,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(start = Gutter, end = Gutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StepProgress(ui.step, steps.size)
                Eyebrow(steps[ui.step])
                Text(ProfileEditorViewModel.title(ui.step), style = MaterialTheme.typography.displaySmall)
            }
            when (ui.step) {
                0 -> BasicsStep(ui, vm)
                1 -> BackgroundStep(ui.draft, vm)
                2 -> CareerStep(ui.draft, vm)
                3 -> LifestyleStep(ui.draft, vm)
                4 -> ValuesStep(ui.draft, vm)
                5 -> PreferencesStep(ui.draft, vm)
                6 -> AboutStep(ui, vm)
            }
        }
    }
}

// --------------------------------------------------------------------------------- helpers

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, supporting: String? = null, capitalize: Boolean = true) {
    AppTextField(
        value, onChange, label = label, supporting = supporting,
        keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None),
    )
}

// ----------------------------------------------------------------------------------- steps

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicsStep(ui: EditorUi, vm: ProfileEditorViewModel) {
    val d = ui.draft
    var picking by rememberSaveable { mutableStateOf(false) }
    Section(title = "Who is creating this profile?", subtitle = "Family is welcome to help — the member always stays in control.") {
        ChoiceChips(ManagedBy.entries, d.managedBy, { if (it == ManagedBy.SELF) "Myself" else it.label }, { m -> vm.update { it.copy(managedBy = m) } })
    }
    Section(title = "About the member") {
        Field(d.firstName, { v -> vm.update { it.copy(firstName = v.take(30)) } }, "First name", "Only your first name and last initial are shown")
        Field(d.lastInitial, { v -> vm.update { it.copy(lastInitial = v.filter(Char::isLetter).take(1).uppercase()) } }, "Last name initial")
        FieldLabel("Gender")
        ChoiceChips(Gender.entries, d.gender, { it.label }, { g -> vm.update { it.copy(gender = g) } })
        FieldLabel("Date of birth")
        Surface(
            onClick = { picking = true }, shape = RoundedCornerShape(18.dp), color = AanganColors.extended.card,
            border = BorderStroke(1.dp, AanganColors.extended.hairline), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        ui.dobLabel.isNotEmpty() -> "${ui.dobLabel} · age ${d.age}"
                        d.age > 0 -> "Age ${d.age} · tap to change"
                        else -> "Choose your date of birth"
                    },
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    color = if (d.age > 0) MaterialTheme.colorScheme.onSurface else AanganColors.extended.muted,
                )
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            }
        }
        Text("Used to confirm legal age. Only your age is shown — never your birth date.", style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
        FieldLabel("Height · ${heightLabel(d.heightCm)}")
        Slider(
            value = d.heightCm.toFloat(), onValueChange = { v -> vm.update { it.copy(heightCm = v.roundToInt()) } }, valueRange = 135f..210f,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
        FieldLabel("Marital status")
        ChoiceChips(MaritalStatus.entries, d.maritalStatus, { it.label }, { m -> vm.update { it.copy(maritalStatus = m) } })
        if (d.maritalStatus != MaritalStatus.NEVER_MARRIED) {
            ListGroup { ToggleRow("Has children", null, d.hasChildren, { v -> vm.update { it.copy(hasChildren = v) } }) }
        }
    }
    if (picking) {
        val today = LocalDate.now()
        val latest = today.minusYears(18).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = null,
            initialDisplayedMonthMillis = today.minusYears(27).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = (today.year - 80)..(today.year - 18),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latest
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { QuietButton("Done", { state.selectedDateMillis?.let(vm::onDob); picking = false }, enabled = state.selectedDateMillis != null) },
            dismissButton = { QuietButton("Cancel", { picking = false }) },
        ) { DatePicker(state = state, showModeToggle = true) }
    }
}

@Composable
private fun BackgroundStep(d: Profile, vm: ProfileEditorViewModel) {
    Section(title = "Where do you live?", subtitle = "We only use your city — never your exact location") {
        ChoiceChips(DemoSeed.Cities.all, DemoSeed.Cities.all.firstOrNull { it.city == d.location.city }, { it.city }, { c -> vm.update { it.copy(location = c) } })
        if (d.location.country != "India") {
            Field(d.residencyStatus.orEmpty(), { v -> vm.update { it.copy(residencyStatus = v.take(60)) } }, "Residency or visa status (optional)", "e.g. Permanent resident, work visa, citizen")
        }
    }
    Section(title = "Faith (optional)", subtitle = "Choose \"Prefer not to say\" if you'd rather not share") {
        ChoiceChips(listOf<Religion?>(null) + Religion.entries, d.religion, { it?.label ?: "Prefer not to say" }, { r -> vm.update { it.copy(religion = r) } })
    }
    Section(title = "Languages") {
        FieldLabel("Mother tongue")
        ChoiceChips(DemoSeed.languageOptions, d.motherTongue, { it }, { l -> vm.update { it.copy(motherTongue = l) } })
        FieldLabel("Other languages you speak")
        MultiChips(DemoSeed.languageOptions, d.languages, { it }, { s -> vm.update { it.copy(languages = s) } })
    }
    Section(title = "Community (optional)", subtitle = "Many members don't share this. It's hidden unless you choose to show it, and never used for matching.") {
        Field(d.community.orEmpty(), { v -> vm.update { it.copy(community = v.take(40).ifBlank { null }) } }, "Community (optional)")
        ListGroup { ToggleRow("Show on my profile", null, d.showCommunity, { v -> vm.update { it.copy(showCommunity = v) } }) }
    }
}

@Composable
private fun CareerStep(d: Profile, vm: ProfileEditorViewModel) {
    Section(title = "Education") {
        ChoiceChips(EducationLevel.entries, d.education, { it.label }, { e -> vm.update { it.copy(education = e) } })
        Field(d.educationField, { v -> vm.update { it.copy(educationField = v.take(60)) } }, "Field of study", "e.g. Computer Science, Medicine, Commerce")
    }
    Section(title = "Work") {
        Field(d.profession, { v -> vm.update { it.copy(profession = v.take(50)) } }, "Profession", "e.g. Software engineer, Doctor, Teacher, Business owner")
        FieldLabel("Annual income (optional)")
        ChoiceChips(IncomeBand.entries, d.incomeBand, { it.label }, { i -> vm.update { it.copy(incomeBand = i) } })
        ListGroup { ToggleRow("Show income on my profile", "Off by default", d.showIncome, { v -> vm.update { it.copy(showIncome = v) } }) }
    }
}

@Composable
private fun LifestyleStep(d: Profile, vm: ProfileEditorViewModel) {
    Section(title = "Food") { ChoiceChips(Diet.entries, d.diet, { it.label }, { x -> vm.update { it.copy(diet = x) } }) }
    Section(title = "Habits") {
        FieldLabel("Smoking")
        ChoiceChips(Habit.entries, d.smoking, { it.label }, { x -> vm.update { it.copy(smoking = x) } })
        FieldLabel("Drinking")
        ChoiceChips(Habit.entries, d.drinking, { it.label }, { x -> vm.update { it.copy(drinking = x) } })
    }
    Section(title = "Family") {
        ChoiceChips(FamilyType.entries, d.familyType, { it.label }, { x -> vm.update { it.copy(familyType = x) } })
        ListGroup { ToggleRow("Open to relocating", "To another city or country after marriage", d.openToRelocate, { v -> vm.update { it.copy(openToRelocate = v) } }) }
    }
}

private val importanceLabels = listOf("Not much", "Somewhat", "Important", "Crucial")

@Composable
private fun ValuesStep(d: Profile, vm: ProfileEditorViewModel) {
    Notice(
        tone = NoticeTone.INFO, title = "The Values Compass",
        text = "These questions most often decide whether a marriage works. There are no right answers — and you choose how much each one matters. ${d.values.size}/12 answered.",
    )
    ValueQuestion.entries.forEachIndexed { qi, q ->
        val a = d.values[q]
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Question ${qi + 1}", Modifier.padding(horizontal = 4.dp))
            Text(q.prompt, fontFamily = DisplayFamily, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            ListGroup {
                q.options.forEachIndexed { i, opt ->
                    if (i > 0) ListDivider()
                    RadioRow(opt, null, selected = a?.answer == i, onClick = {
                        vm.update { it.copy(values = it.values + (q to ValueAnswer(i, a?.importance ?: 2))) }
                    })
                }
            }
            if (a != null) {
                Text("How much does this matter to you?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
                SegmentedTabs(importanceLabels, a.importance, { imp -> vm.update { it.copy(values = it.values + (q to a.copy(importance = imp))) } })
            }
        }
    }
}

@Composable
private fun PreferencesStep(d: Profile, vm: ProfileEditorViewModel) {
    val p = d.preferences
    val minAllowed = d.seeking.minimumMarriageAge()
    Section(title = "Age · ${p.minAge}–${p.maxAge}") {
        RangeSlider(
            value = p.minAge.toFloat()..p.maxAge.toFloat(),
            onValueChange = { r -> vm.prefs { it.copy(minAge = r.start.roundToInt(), maxAge = r.endInclusive.roundToInt()) } },
            valueRange = minAllowed.toFloat()..70f,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
    Section(title = "Marital status") {
        MultiChips(MaritalStatus.entries, p.maritalStatuses, { it.label }, { s -> vm.prefs { it.copy(maritalStatuses = s) } })
    }
    Section(title = "Faith", subtitle = "Leave empty for \"doesn't matter\"") {
        MultiChips(Religion.entries, p.religions, { it.label }, { s -> vm.prefs { it.copy(religions = s) } })
    }
    Section(title = "Mother tongue", subtitle = "Leave empty for \"doesn't matter\"") {
        MultiChips(DemoSeed.languageOptions, p.motherTongues, { it }, { s -> vm.prefs { it.copy(motherTongues = s) } })
    }
    Section(title = "Diet", subtitle = "Leave empty for \"doesn't matter\"") {
        MultiChips(Diet.entries, p.diets, { it.label }, { s -> vm.prefs { it.copy(diets = s) } })
    }
    Section(title = "Minimum education") {
        ChoiceChips(listOf<EducationLevel?>(null) + EducationLevel.entries, p.minEducation, { it?.label ?: "Any" }, { e -> vm.prefs { it.copy(minEducation = e) } })
    }
    Section(title = "Distance", subtitle = p.maxDistanceKm?.let { "Within $it km" } ?: "Anywhere") {
        val steps = listOf(null, 25, 50, 100, 250, 500, 1000, 2000)
        ChoiceChips(steps, p.maxDistanceKm, { it?.let { km -> "$km km" } ?: "Anywhere" }, { km -> vm.prefs { it.copy(maxDistanceKm = km) } })
    }
    ListGroup(title = "Habits and family") {
        ToggleRow("Okay with a partner who smokes", null, p.acceptsSmoking, { v -> vm.prefs { it.copy(acceptsSmoking = v) } })
        ListDivider()
        ToggleRow("Okay with a partner who drinks", null, p.acceptsDrinking, { v -> vm.prefs { it.copy(acceptsDrinking = v) } })
        ListDivider()
        ToggleRow("Open to a partner with children", null, p.acceptsChildren, { v -> vm.prefs { it.copy(acceptsChildren = v) } })
    }
    Section(title = "Must-haves · up to 4", subtitle = "Only true deal-breakers. Everything else stays a soft preference that still counts — so you don't miss great people.") {
        MultiChips(PreferenceKey.entries, p.mustHaves, { it.label }, { s -> vm.prefs { it.copy(mustHaves = s) } }, max = 4)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutStep(ui: EditorUi, vm: ProfileEditorViewModel) {
    val d = ui.draft
    val context = LocalContext.current
    var tone by rememberSaveable { mutableStateOf("Warm") }
    // The system photo picker needs no storage permission and only shares the chosen image.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.addPhoto(context, uri) }
    val total = d.photos.size + ui.pendingPhotos.size

    Section(title = "Photos · $total of ${ProfileEditorViewModel.MAX_PHOTOS}", subtitle = "Clear, recent photos of just you get far more replies. Location and camera details are removed before upload.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
            d.photos.forEachIndexed { i, ph ->
                PhotoTile(Modifier.weight(1f), primary = i == 0, onRemove = { vm.removePhoto(ph.id) }, onPrimary = if (i > 0) ({ vm.makePrimary(ph.id) }) else null) {
                    ProfilePhoto(d.copy(photos = listOf(ph)), Modifier.fillMaxSize(), shape = RoundedCornerShape(18.dp))
                }
            }
            ui.pendingPhotos.forEachIndexed { i, pp ->
                PhotoTile(Modifier.weight(1f), primary = d.photos.isEmpty() && i == 0, onRemove = { vm.removePhoto(pp.id) }, onPrimary = null) {
                    AsyncImage(model = pp.jpeg, contentDescription = "Selected photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (total < ProfileEditorViewModel.MAX_PHOTOS) {
                Box(
                    Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, AanganColors.extended.hairline, RoundedCornerShape(18.dp))
                        .selectable(selected = false, role = Role.Button, enabled = !ui.photoBusy) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.photoBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            // Keep the grid's last row aligned.
            repeat((3 - ((total + if (total < ProfileEditorViewModel.MAX_PHOTOS) 1 else 0) % 3)) % 3) { Box(Modifier.weight(1f)) }
        }
    }
    ListGroup(title = "Who can see my photos") {
        PhotoVisibility.entries.forEachIndexed { i, v ->
            if (i > 0) ListDivider()
            RadioRow(v.label, v.description, selected = d.photoVisibility == v, onClick = { vm.update { it.copy(photoVisibility = v) } })
        }
    }
    Section(title = "Interests · up to 8", subtitle = "They power icebreakers and shared-interest matching") {
        MultiChips(DemoSeed.interestOptions, d.interests, { it }, { s -> vm.update { it.copy(interests = s) } }, max = 8)
    }
    Section(title = "About me") {
        AppTextField(d.about, { v -> vm.update { it.copy(about = v.take(1200)) } }, placeholder = "A few lines about you, your days, and what you hope for", singleLine = false, minLines = 5, supporting = "${d.about.length}/1200")
        val hasContact = remember(d.about) { PiiDetector.containsContactDetails(d.about) }
        if (hasContact) {
            Notice(tone = NoticeTone.CAUTION, text = "Your bio seems to include contact details. For your safety these aren't allowed — share your number privately after you match.")
        }
        FieldLabel("Help me write")
        ChoiceChips(listOf("Warm", "Simple", "Fun"), tone, { it }, { tone = it })
        SecondaryButton(if (d.about.isBlank()) "Draft my bio" else "Rewrite my bio", { vm.draftBio(tone) }, Modifier.fillMaxWidth(), loading = ui.aiBusy, icon = Icons.Filled.AutoAwesome)
        Text(
            if (ui.aiEnabled) "AI drafts use only your answers here (never your name or number). Review before saving."
            else "AI assistance is off, so we'll use a simple template. You can turn AI on in Privacy Center.",
            style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted,
        )
    }
}

@Composable
private fun PhotoTile(modifier: Modifier, primary: Boolean, onRemove: () -> Unit, onPrimary: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(modifier.aspectRatio(0.8f).clip(RoundedCornerShape(18.dp))) {
        content()
        CircleIconButton(
            Icons.Filled.Close, "Remove photo", onRemove, size = 30.dp,
            container = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), content = androidx.compose.ui.graphics.Color.White, bordered = false,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
        if (primary) {
            Text(
                "Main", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        } else if (onPrimary != null) {
            CircleIconButton(
                Icons.Outlined.Star, "Make this your main photo", onPrimary, size = 30.dp,
                container = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), content = androidx.compose.ui.graphics.Color.White, bordered = false,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}
