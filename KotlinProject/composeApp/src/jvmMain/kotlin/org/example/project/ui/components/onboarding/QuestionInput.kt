package org.example.project.ui.components.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.core.onboarding.OnboardingOption
import org.example.project.core.onboarding.OnboardingQuestion
import org.example.project.core.onboarding.OnboardingResponse
import org.example.project.ui.theme.WordBridgeColors

@Composable
fun QuestionInputPanel(
    question: OnboardingQuestion,
    onSubmit: (OnboardingResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (question.inputType) {
            org.example.project.core.onboarding.OnboardingInputType.TEXT -> TextInput(question, onSubmit)
            org.example.project.core.onboarding.OnboardingInputType.SINGLE_SELECT -> SingleSelect(question, onSubmit)
            org.example.project.core.onboarding.OnboardingInputType.MULTI_SELECT -> MultiSelect(question, onSubmit)
            org.example.project.core.onboarding.OnboardingInputType.SCALE -> ScaleInput(question, onSubmit)
        }
    }
}

@Composable
private fun TextInput(
    question: OnboardingQuestion,
    onSubmit: (OnboardingResponse.Text) -> Unit,
) {
    val textState = remember { mutableStateOf("") }

    OutlinedTextField(
        value = textState.value,
        onValueChange = { textState.value = it },
        label = { Text(question.placeholder ?: "Type your answer") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (question.allowsVoiceInput) {
                IconButton(onClick = { /* voice input hook */ }) {
                    Text("🎤")
                }
            }
        },
    )

    Button(
        onClick = { onSubmit(OnboardingResponse.Text(textState.value)) },
        modifier =
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
        enabled = textState.value.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = WordBridgeColors.PrimaryPurple),
    ) {
        Text("Submit", color = Color.White)
    }
}

@Composable
private fun SingleSelect(
    question: OnboardingQuestion,
    onSubmit: (OnboardingResponse.SingleChoice) -> Unit,
) {
    val selected = remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Horizontal scrollable options container
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(question.options.size) { index ->
                val option = question.options[index]
                QuickChip(
                    label = optionLabel(option),
                    isSelected = selected.value == option.id,
                    onClick = {
                        println("??? Selected option: ${option.id} - ${option.label}")
                        selected.value = option.id
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val selectedId = selected.value
                println("??? Button clicked with selectedId: $selectedId")
                val option = question.options.find { it.id == selectedId }
                if (option != null) {
                    println("??? Submitting response: ${option.label}")
                    onSubmit(
                        OnboardingResponse.SingleChoice(
                            optionId = option.id,
                            label = option.label,
                            value = option.value,
                        ),
                    )
                } else {
                    println("??? ERROR: No option found for ID: $selectedId")
                }
            },
            enabled = selected.value != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WordBridgeColors.PrimaryPurple),
        ) {
            Text("Continue", color = Color.White)
        }
    }
}

@Composable
private fun MultiSelect(
    question: OnboardingQuestion,
    onSubmit: (OnboardingResponse.MultiChoice) -> Unit,
) {
    val selectedIds = remember { mutableStateListOf<String>() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(question.options.size) { idx ->
                val option = question.options[idx]
                val isSelected = selectedIds.contains(option.id)
                QuickChip(
                    label = optionLabel(option),
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selectedIds.remove(option.id)
                        } else {
                            selectedIds.add(option.id)
                        }
                    },
                )
            }
        }

        Button(
            onClick = {
                val selectedOptions = question.options.filter { selectedIds.contains(it.id) }
                onSubmit(
                    OnboardingResponse.MultiChoice(
                        optionIds = selectedOptions.map { it.id },
                        labels = selectedOptions.map { it.label },
                        values = selectedOptions.map { it.value },
                    ),
                )
            },
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WordBridgeColors.PrimaryPurple),
        ) {
            Text("Confirm", color = Color.White)
        }
    }
}

@Composable
private fun ScaleInput(
    question: OnboardingQuestion,
    onSubmit: (OnboardingResponse.Scale) -> Unit,
) {
    val slider = remember { mutableStateOf((question.minScale + question.maxScale) / 2f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${question.minScale}", color = WordBridgeColors.TextSecondary)
            Text("${slider.value.toInt()}", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp))
            Text("${question.maxScale}", color = WordBridgeColors.TextSecondary)
        }

        Slider(
            value = slider.value,
            onValueChange = { slider.value = it },
            valueRange = question.minScale.toFloat()..question.maxScale.toFloat(),
            steps = question.maxScale - question.minScale - 1,
            colors =
                SliderDefaults.colors(
                    thumbColor = WordBridgeColors.PrimaryPurple,
                    activeTrackColor = WordBridgeColors.PrimaryPurple,
                    inactiveTrackColor = WordBridgeColors.PrimaryPurple.copy(alpha = 0.2f),
                ),
        )

        Button(
            onClick = {
                onSubmit(
                    OnboardingResponse.Scale(
                        score = slider.value.toInt(),
                        min = question.minScale,
                        max = question.maxScale,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WordBridgeColors.PrimaryPurple),
        ) {
            Text("Log my confidence", color = Color.White)
        }
    }
}

private fun optionLabel(option: OnboardingOption): String {
    return buildString {
        option.emoji?.let { append(it).append(" ") }
        append(option.label)
    }
}
