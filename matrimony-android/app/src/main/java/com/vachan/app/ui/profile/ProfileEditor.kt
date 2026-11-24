package com.vachan.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.EducationLevel
import com.vachan.app.core.model.FamilyType
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.Habit
import com.vachan.app.core.model.IncomeBand
import com.vachan.app.core.model.Location
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
import com.vachan.app.core.safety.PiiDetector
import com.vachan.app.data.AiRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.data.demo.DemoSeed
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.components.StepProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

private val emptyProfile = Profile(
    id = "", firstName = "", lastInitial = "", gender = Gender.FEMALE, age = 0, heightCm = 165,
    location = DemoSeed.Cities.mumbai, motherTongue = "Hindi", education = EducationLevel.BACHELORS,
    profession = "", diet = Diet.VEGETARIAN, languages = setOf("English", "Hindi"),
    preferences = PartnerPreferences(minAge = 25, maxAge = 33),
)

data class EditorUi(
    val draft: Profile = emptyProfile,
    val step: Int = 0,
    val dob: String = "",
    val saving: Boolean = false,
    val aiBusy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val aiEnabled: Boolean = false,
)

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val ai: AiRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditorUi(draft = profiles.myProfile.value ?: emptyProfile, aiEnabled = ai.aiEnabled.value))
    val ui = _ui.asStateFlow()

    fun update(transform: (Profile) -> Profile) { _ui.value = _ui.value.copy(draft = transform(_ui.value.draft), error = null) }
    fun prefs(transform: (PartnerPreferences) -> PartnerPreferences) = update { it.copy(preferences = transform(it.preferences)) }

    fun onDob(v: String) {
        _ui.value = _ui.value.copy(dob = v.filter { it.isDigit() || it == '/' }.take(10))
        parseAge(_ui.value.dob)?.let { age -> update { it.copy(age = age) } }
    }

    private fun parseAge(dob: String): Int? = runCatching {
        Period.between(LocalDate.parse(dob, DateTimeFormatter.ofPattern("dd/MM/yyyy")), LocalDate.now()).years
    }.getOrNull()

    fun goTo(step: Int) {
        validate(_ui.value.step)?.let { if (step > _ui.value.step) { _ui.value = _ui.value.copy(error = it); return } }
        _ui.value = _ui.value.copy(step = step.coerceIn(0, STEPS.lastIndex), error = null)
    }

    private fun validate(step: Int): String? {
        val d = _ui.value.draft
        return when (step) {
            0 -> when {
                d.firstName.isBlank() -> "Please enter a first name"
                d.age == 0 -> "Please enter date of birth as DD/MM/YYYY"
                d.age < d.gender.minimumMarriageAge() -> "Members must be at least ${d.gender.minimumMarriageAge()} (legal marriageable age)"
                d.age > 80 -> "Please check the date of birth"
                else -> null
            }
            2 -> if (d.profession.isBlank()) "Please add your profession" else null
            4 -> if (d.values.size < 8) "Please answer at least 8 questions — they're the heart of good matching" else null
            6 -> when {
                d.about.length < 40 -> "Write at least a couple of sentences about yourself (or tap \"Help me write\")"
                PiiDetector.containsContactDetails(d.about) -> "Please remove contact details from your bio. You can share your number privately after you match."
                else -> null
            }
            else -> null
        }
    }

    fun draftBio(tone: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(aiBusy = true)
        val bio = ai.draftBio(_ui.value.draft, tone, settings.settings.value.language)
        update { it.copy(about = bio) }
        _ui.value = _ui.value.copy(aiBusy = false)
    }

    fun save() = viewModelScope.launch {
        validate(_ui.value.step)?.let { _ui.value = _ui.value.copy(error = it); return@launch }
        _ui.value = _ui.value.copy(saving = true)
        val d = _ui.value.draft.let { if (it.photos.isEmpty()) it.copy(photos = listOf(Photo("ph_me", seed = it.firstName.hashCode()))) else it }
        profiles.saveMyProfile(d)
            .onSuccess { _ui.value = _ui.value.copy(saving = false, saved = true) }
            .onFailure { _ui.value = _ui.value.copy(saving = false, error = it.message ?: "Couldn't save your profile") }
    }

    companion object {
        val STEPS = listOf("Basics", "Background", "Career", "Lifestyle", "Values Compass", "Partner preferences", "About & photos")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOnboarding) "Create your profile" else "Edit profile") },
                navigationIcon = {
                    if (ui.step > 0) IconButton(onClick = { vm.goTo(ui.step - 1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous step") }
                    else if (!isOnboarding) IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Close") }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isOnboarding) {
                        OutlinedButton(onClick = vm::save, enabled = !ui.saving, modifier = Modifier.weight(1f).height(52.dp)) { Text("Save now") }
                    }
                    Button(
                        onClick = { if (ui.step == steps.lastIndex) vm.save() else vm.goTo(ui.step + 1) },
                        enabled = !ui.saving,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (ui.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(if (ui.step == steps.lastIndex) (if (isOnboarding) "Finish" else "Save") else "Next")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepProgress(ui.step, steps.size)
            Text("Step ${ui.step + 1} of ${steps.size} · ${steps[ui.step]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            when (ui.step) {
                0 -> BasicsStep(ui, vm)
                1 -> BackgroundStep(ui.draft, vm)
                2 -> CareerStep(ui.draft, vm)
                3 -> LifestyleStep(ui.draft, vm)
                4 -> ValuesStep(ui.draft, vm)
                5 -> PreferencesStep(ui.draft, vm)
                6 -> AboutStep(ui, vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --------------------------------------------------------------------------------- helpers

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChips(options: List<T>, selected: Set<T>, label: (T) -> String, onChange: (Set<T>) -> Unit, max: Int = Int.MAX_VALUE) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val on = o in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - o else if (selected.size < max) selected + o else selected) },
                label = { Text(label(o)) },
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, supporting: String? = null, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        supportingText = supporting?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledSwitch(text: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ----------------------------------------------------------------------------------- steps

@Composable
private fun BasicsStep(ui: EditorUi, vm: ProfileEditorViewModel) {
    val d = ui.draft
    SectionCard(title = "Who is creating this profile?", subtitle = "Profiles created by family are welcome — the member always stays in control.") {
        ChoiceChips(ManagedBy.entries, d.managedBy, { if (it == ManagedBy.SELF) "Myself" else it.label }, { m -> vm.update { it.copy(managedBy = m) } })
    }
    SectionCard(title = "About the member") {
        Field(d.firstName, { v -> vm.update { it.copy(firstName = v.take(30)) } }, "First name", "Only your first name and last initial are shown")
        Field(d.lastInitial, { v -> vm.update { it.copy(lastInitial = v.take(1).uppercase()) } }, "Last name initial")
        Text("Gender", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Gender.entries, d.gender, { it.label }, { g -> vm.update { it.copy(gender = g) } })
        Field(ui.dob, vm::onDob, "Date of birth (DD/MM/YYYY)", if (d.age > 0) "Age ${d.age} — only your age is shown, never your birth date" else "Used to confirm legal age; only age is shown", KeyboardType.Number)
        Text("Height: ${heightLabel(d.heightCm)}", style = MaterialTheme.typography.labelLarge)
        Slider(value = d.heightCm.toFloat(), onValueChange = { v -> vm.update { it.copy(heightCm = v.roundToInt()) } }, valueRange = 135f..210f)
        Text("Marital status", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(MaritalStatus.entries, d.maritalStatus, { it.label }, { m -> vm.update { it.copy(maritalStatus = m) } })
        if (d.maritalStatus != MaritalStatus.NEVER_MARRIED) {
            LabeledSwitch("Has children", checked = d.hasChildren) { v -> vm.update { it.copy(hasChildren = v) } }
        }
    }
}

@Composable
private fun BackgroundStep(d: Profile, vm: ProfileEditorViewModel) {
    SectionCard(title = "Where do you live?", subtitle = "We only use your city — never your exact location") {
        ChoiceChips(DemoSeed.Cities.all, DemoSeed.Cities.all.firstOrNull { it.city == d.location.city }, { it.city }, { c -> vm.update { it.copy(location = c) } })
        if (d.location.country != "India") {
            Field(d.residencyStatus.orEmpty(), { v -> vm.update { it.copy(residencyStatus = v.take(60)) } }, "Residency / visa status (optional)", "e.g. Permanent resident, work visa, citizen")
        }
    }
    SectionCard(title = "Religion (optional)", subtitle = "Choose \"Prefer not to say\" if you'd rather not share") {
        ChoiceChips(listOf<Religion?>(null) + Religion.entries, d.religion, { it?.label ?: "Prefer not to say" }, { r -> vm.update { it.copy(religion = r) } })
    }
    SectionCard(title = "Languages") {
        Text("Mother tongue", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(DemoSeed.languageOptions, d.motherTongue, { it }, { l -> vm.update { it.copy(motherTongue = l) } })
        Text("Other languages you speak", style = MaterialTheme.typography.labelLarge)
        MultiChips(DemoSeed.languageOptions, d.languages, { it }, { s -> vm.update { it.copy(languages = s) } })
    }
    SectionCard(title = "Community (optional)", subtitle = "Many members don't share this. It's hidden from others unless you choose to show it.") {
        Field(d.community.orEmpty(), { v -> vm.update { it.copy(community = v.take(40).ifBlank { null }) } }, "Community / caste (optional)")
        LabeledSwitch("Show on my profile", checked = d.showCommunity) { v -> vm.update { it.copy(showCommunity = v) } }
    }
}

@Composable
private fun CareerStep(d: Profile, vm: ProfileEditorViewModel) {
    SectionCard(title = "Education") {
        ChoiceChips(EducationLevel.entries, d.education, { it.label }, { e -> vm.update { it.copy(education = e) } })
        Field(d.educationField, { v -> vm.update { it.copy(educationField = v.take(60)) } }, "Field of study", "e.g. Computer Science, Medicine, Commerce")
    }
    SectionCard(title = "Work") {
        Field(d.profession, { v -> vm.update { it.copy(profession = v.take(50)) } }, "Profession", "e.g. Software engineer, Doctor, Teacher, Business owner")
        Text("Annual income (optional)", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(IncomeBand.entries, d.incomeBand, { it.label }, { i -> vm.update { it.copy(incomeBand = i) } })
        LabeledSwitch("Show income on my profile", "Off by default. Used only to answer filters if you allow it.", d.showIncome) { v -> vm.update { it.copy(showIncome = v) } }
    }
}

@Composable
private fun LifestyleStep(d: Profile, vm: ProfileEditorViewModel) {
    SectionCard(title = "Food") { ChoiceChips(Diet.entries, d.diet, { it.label }, { x -> vm.update { it.copy(diet = x) } }) }
    SectionCard(title = "Habits") {
        Text("Smoking", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Habit.entries, d.smoking, { it.label }, { x -> vm.update { it.copy(smoking = x) } })
        Text("Drinking", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Habit.entries, d.drinking, { it.label }, { x -> vm.update { it.copy(drinking = x) } })
    }
    SectionCard(title = "Family") {
        ChoiceChips(FamilyType.entries, d.familyType, { it.label }, { x -> vm.update { it.copy(familyType = x) } })
        LabeledSwitch("Open to relocating", "To another city or country after marriage", d.openToRelocate) { v -> vm.update { it.copy(openToRelocate = v) } }
    }
}

@Composable
private fun ValuesStep(d: Profile, vm: ProfileEditorViewModel) {
    Banner(
        tone = BannerTone.INFO, title = "The Values Compass",
        text = "These are the questions that most often decide whether a marriage works. There are no right answers — and you choose how much each one matters.",
    )
    ValueQuestion.entries.forEach { q ->
        val a = d.values[q]
        SectionCard(title = q.prompt) {
            q.options.forEachIndexed { i, opt ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = a?.answer == i, role = Role.RadioButton, onClick = {
                        vm.update { it.copy(values = it.values + (q to ValueAnswer(i, a?.importance ?: 2))) }
                    }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = a?.answer == i, onClick = null)
                    Text(opt, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (a != null) {
                Text("How much does this matter to you?", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(0, 1, 2, 3), a.importance, { listOf("Doesn't matter", "Somewhat", "Important", "Very important")[it] }, { imp ->
                    vm.update { it.copy(values = it.values + (q to a.copy(importance = imp))) }
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesStep(d: Profile, vm: ProfileEditorViewModel) {
    val p = d.preferences
    val minAllowed = d.seeking.minimumMarriageAge()
    SectionCard(title = "Age", subtitle = "${p.minAge}–${p.maxAge} years") {
        RangeSlider(
            value = p.minAge.toFloat()..p.maxAge.toFloat(),
            onValueChange = { r -> vm.prefs { it.copy(minAge = r.start.roundToInt(), maxAge = r.endInclusive.roundToInt()) } },
            valueRange = minAllowed.toFloat()..70f,
        )
    }
    SectionCard(title = "Marital status") {
        MultiChips(MaritalStatus.entries, p.maritalStatuses, { it.label }, { s -> vm.prefs { it.copy(maritalStatuses = s) } })
    }
    SectionCard(title = "Religion", subtitle = "Leave empty for \"doesn't matter\"") {
        MultiChips(Religion.entries, p.religions, { it.label }, { s -> vm.prefs { it.copy(religions = s) } })
    }
    SectionCard(title = "Mother tongue", subtitle = "Leave empty for \"doesn't matter\"") {
        MultiChips(DemoSeed.languageOptions, p.motherTongues, { it }, { s -> vm.prefs { it.copy(motherTongues = s) } })
    }
    SectionCard(title = "Diet", subtitle = "Leave empty for \"doesn't matter\"") {
        MultiChips(Diet.entries, p.diets, { it.label }, { s -> vm.prefs { it.copy(diets = s) } })
    }
    SectionCard(title = "Minimum education") {
        ChoiceChips(listOf<EducationLevel?>(null) + EducationLevel.entries, p.minEducation, { it?.label ?: "Any" }, { e -> vm.prefs { it.copy(minEducation = e) } })
    }
    SectionCard(title = "Distance", subtitle = p.maxDistanceKm?.let { "Within $it km" } ?: "Anywhere") {
        val steps = listOf(null, 25, 50, 100, 250, 500, 1000, 2000)
        ChoiceChips(steps, p.maxDistanceKm, { it?.let { km -> "$km km" } ?: "Anywhere" }, { km -> vm.prefs { it.copy(maxDistanceKm = km) } })
    }
    SectionCard(title = "Habits and family") {
        LabeledSwitch("Okay with a partner who smokes", checked = p.acceptsSmoking) { v -> vm.prefs { it.copy(acceptsSmoking = v) } }
        LabeledSwitch("Okay with a partner who drinks", checked = p.acceptsDrinking) { v -> vm.prefs { it.copy(acceptsDrinking = v) } }
        LabeledSwitch("Open to a partner with children", checked = p.acceptsChildren) { v -> vm.prefs { it.copy(acceptsChildren = v) } }
    }
    SectionCard(title = "Must-haves", subtitle = "Pick only true deal-breakers. Everything else becomes a soft preference that still counts — so you don't miss great people.") {
        MultiChips(PreferenceKey.entries, p.mustHaves, { it.label }, { s -> vm.prefs { it.copy(mustHaves = s) } }, max = 4)
    }
}

@Composable
private fun AboutStep(ui: EditorUi, vm: ProfileEditorViewModel) {
    val d = ui.draft
    var tone by rememberSaveable { mutableStateOf("Warm") }
    SectionCard(title = "Interests", subtitle = "Pick up to 8 — they power icebreakers and shared-interest matching") {
        MultiChips(DemoSeed.interestOptions, d.interests, { it }, { s -> vm.update { it.copy(interests = s) } }, max = 8)
    }
    SectionCard(title = "About me") {
        OutlinedTextField(
            value = d.about, onValueChange = { v -> vm.update { it.copy(about = v.take(1200)) } },
            label = { Text("Write a few lines about yourself") }, minLines = 5, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${d.about.length}/1200") },
        )
        val hasContact = remember(d.about) { PiiDetector.containsContactDetails(d.about) }
        if (hasContact) {
            Banner(tone = BannerTone.CAUTION, text = "Your bio seems to include contact details. For your safety these aren't allowed — share your number privately after you match.")
        }
        Text("Help me write", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf("Warm", "Simple", "Fun"), tone, { it }, { tone = it })
        OutlinedButton(onClick = { vm.draftBio(tone) }, enabled = !ui.aiBusy) {
            if (ui.aiBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (d.about.isBlank()) "Draft my bio" else "Rewrite my bio")
            }
        }
        Text(
            if (ui.aiEnabled) "AI drafts use only your answers in this form (never your name or number). Review before saving."
            else "AI assistance is off, so we'll use a simple template. You can turn AI on in Privacy Center.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard(title = "Photo", subtitle = "Clear, recent photos of just you get far more responses") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfilePhoto(d.copy(photos = d.photos.ifEmpty { listOf(Photo("preview", seed = d.firstName.hashCode())) }), size = 96.dp)
            Text(
                "Photo upload connects to secure storage when the backend is configured. Photos are checked for AI-generated or stolen images, and location data (EXIF) is removed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
        }
        Text("Who can see my photos?", style = MaterialTheme.typography.labelLarge)
        PhotoVisibility.entries.forEach { v ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = d.photoVisibility == v, role = Role.RadioButton, onClick = { vm.update { it.copy(photoVisibility = v) } }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = d.photoVisibility == v, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(v.label)
                    Text(v.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Suppress("unused")
private fun Location.label() = "$city, $country"
