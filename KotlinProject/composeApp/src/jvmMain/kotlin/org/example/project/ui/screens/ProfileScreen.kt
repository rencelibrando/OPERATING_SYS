package org.example.project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.domain.model.*
import org.example.project.presentation.viewmodel.ProfileViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.jetbrains.skia.Image
import org.example.project.core.auth.User as AuthUser

@Composable
fun ProfileScreen(
    authenticatedUser: AuthUser? = null,
    viewModel: ProfileViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val userProfile by viewModel.userProfile
    val profileCompletion by viewModel.profileCompletion
    val isEditing by viewModel.isEditing
    val isLoading by viewModel.isLoading
    val isSaving by viewModel.isSaving
    val editingSection by viewModel.editingSection
    val editingPersonalInfo by viewModel.editingPersonalInfo
    val editingLearningProfile by viewModel.editingLearningProfile

    LaunchedEffect(authenticatedUser) {
        authenticatedUser?.let { authUser ->
            println("ProfileScreen - Initializing profile for user: ${authUser.email}")
            viewModel.initializeWithAuthenticatedUser(authUser)
        }
    }

    LaunchedEffect(userProfile, isLoading) {
        if (userProfile.personalInfo.firstName.isNotEmpty()) {
            println("ProfileScreen - Profile loaded successfully: ${userProfile.personalInfo.fullName}")
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Header with user avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )

            // User Avatar Circle (top right)
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF3B82F6), // blue-500
                                            Color(0xFFA855F7), // purple-500
                                        ),
                                ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userProfile.personalInfo.initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            }
        } else {
            // Profile Header Card with Gradient
            ProfileHeaderGradient(
                profile = userProfile,
                completion = profileCompletion,
                isEditingPicture = viewModel.isEditingProfilePicture.value,
                tempImageBytes = viewModel.tempProfileImageBytes.value,
                onStartEditPhoto = viewModel::onStartProfilePictureEdit,
                onSavePhoto = viewModel::onSaveProfilePicture,
                onCancelEditPhoto = viewModel::onCancelProfilePictureEdit,
                isSaving = isSaving,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Personal Information
            PersonalInformationSection(
                isEditing = editingSection == ProfileSection.PERSONAL_INFO,
                onEdit = { viewModel.onStartSectionEdit(ProfileSection.PERSONAL_INFO) },
                onSave = viewModel::onSaveSectionChanges,
                onCancel = viewModel::onCancelSectionEdit,
                personalInfo = if (editingSection == ProfileSection.PERSONAL_INFO) editingPersonalInfo else userProfile.personalInfo,
                onUpdateField = viewModel::onUpdatePersonalInfoField,
                onUpdateTargetLanguages = viewModel::onUpdateTargetLanguages,
                isEmailVerified = userProfile.accountInfo.isEmailVerified,
                onVerifyEmail = viewModel::onVerifyEmail,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Learning Profile with Gradient
            LearningProfileGradientSection(
                isEditing = editingSection == ProfileSection.LEARNING_PROFILE,
                onEdit = { viewModel.onStartSectionEdit(ProfileSection.LEARNING_PROFILE) },
                onSave = viewModel::onSaveSectionChanges,
                onCancel = viewModel::onCancelSectionEdit,
                learningProfile = if (editingSection == ProfileSection.LEARNING_PROFILE) editingLearningProfile else userProfile.learningProfile,
                onUpdateField = viewModel::onUpdateLearningProfileField,
                onUpdateList = viewModel::onUpdateLearningProfileList,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Remove Account & Security section
        }
    }
}

@Composable
private fun ProfileHeaderGradient(
    profile: UserProfile,
    completion: ProfileCompletion,
    isEditingPicture: Boolean = false,
    tempImageBytes: ByteArray? = null,
    onStartEditPhoto: () -> Unit = {},
    onSavePhoto: () -> Unit = {},
    onCancelEditPhoto: () -> Unit = {},
    isSaving: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(24.dp),
                    ),
        ) {
            // Decorative blur circle
            Box(
                modifier =
                    Modifier
                        .size(384.dp)
                        .offset(x = 200.dp, y = (-100).dp)
                        .background(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color(0x333B82F6), // blue-400/20
                                            Color(0x33A855F7), // purple-400/20
                                            Color.Transparent,
                                        ),
                                ),
                            shape = CircleShape,
                        ),
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar with Camera Button
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    // Main Avatar
                    Box(
                        modifier =
                            Modifier
                                .size(128.dp)
                                .clip(CircleShape)
                                .background(
                                    brush =
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFF3B82F6), // blue-500
                                                    Color(0xFFA855F7), // purple-500
                                                ),
                                        ),
                                )
                                .clickable(onClick = onStartEditPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isEditingPicture && tempImageBytes != null) {
                            Image(
                                bitmap = Image.makeFromEncoded(tempImageBytes).asImageBitmap(),
                                contentDescription = "New Profile Picture",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = profile.personalInfo.initials,
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }

                    // Camera Button
                    if (!isEditingPicture) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.dp, Color(0xFFE9D5FF), CircleShape) // purple-200
                                    .clickable(onClick = onStartEditPhoto),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "📷", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text =
                        if (profile.personalInfo.fullName.isBlank()) {
                            if (profile.personalInfo.firstName.isNotBlank() || profile.personalInfo.lastName.isNotBlank()) {
                                "${profile.personalInfo.firstName} ${profile.personalInfo.lastName}".trim()
                            } else {
                                "Complete your profile"
                            }
                        } else {
                            profile.personalInfo.fullName
                        },
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = profile.personalInfo.email.ifBlank { "No email set" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Profile Completion Progress
                Column(
                    modifier = Modifier.widthIn(max = 448.dp).fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Profile Completion",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${completion.completionPercentage}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(completion.completionPercentage / 100f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        brush =
                                            Brush.horizontalGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFF3B82F6), // blue-500
                                                        Color(0xFFA855F7), // purple-500
                                                    ),
                                            ),
                                    ),
                        )
                    }
                }

                // Save/Cancel buttons when editing
                if (isEditingPicture) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onCancelEditPhoto,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = onSavePhoto,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                ),
                            modifier =
                                Modifier.background(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFA855F7), // purple-500
                                                    Color(0xFF3B82F6), // blue-500
                                                ),
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            enabled = !isSaving,
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Save Picture", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalInformationSection(
    isEditing: Boolean,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    personalInfo: PersonalInfo?,
    onUpdateField: (ProfileField, String) -> Unit,
    onUpdateTargetLanguages: (List<String>) -> Unit,
    isEmailVerified: Boolean,
    onVerifyEmail: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush =
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFF60A5FA), // blue-400
                                                    Color(0xFFA855F7), // purple-500
                                                ),
                                        ),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "👤", style = MaterialTheme.typography.headlineMedium)
                    }

                    Text(
                        text = "Personal Information",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (isEditing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = onSave,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                ),
                            modifier =
                                Modifier.background(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFFA855F7), // purple-500
                                                    Color(0xFF3B82F6), // blue-500
                                                ),
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                } else {
                    TextButton(onClick = onEdit) {
                        Text("✏️ Edit", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            personalInfo?.let { info ->
                if (isEditing) {
                    PersonalInfoEditForm(
                        personalInfo = info,
                        onUpdateField = onUpdateField,
                        onUpdateTargetLanguages = onUpdateTargetLanguages,
                        isEmailVerified = isEmailVerified,
                        onVerifyEmail = onVerifyEmail,
                    )
                } else {
                    PersonalInfoViewGrid(
                        personalInfo = info,
                        isEmailVerified = isEmailVerified,
                        onVerifyEmail = onVerifyEmail,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalInfoViewGrid(
    personalInfo: PersonalInfo,
    isEmailVerified: Boolean,
    onVerifyEmail: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            InfoFieldDisplay(
                label = "First Name *",
                value = personalInfo.firstName,
                modifier = Modifier.weight(1f),
            )
            InfoFieldDisplay(
                label = "Last Name *",
                value = personalInfo.lastName,
                modifier = Modifier.weight(1f),
            )
        }

        InfoFieldDisplay(
            label = "Email *",
            value = personalInfo.email,
            trailingContent = {
                if (!isEmailVerified) {
                    Button(
                        onClick = onVerifyEmail,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA855F7),
                                contentColor = Color.White,
                            ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Verify", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            InfoFieldDisplay(
                label = "Date of Birth",
                value = personalInfo.dateOfBirth ?: "YYYY-MM-DD",
                modifier = Modifier.weight(1f),
            )
            InfoFieldDisplay(
                label = "Location",
                value = personalInfo.location ?: "City, Country",
                modifier = Modifier.weight(1f),
            )
        }

        InfoFieldDisplay(
            label = "Bio",
            value = personalInfo.bio ?: "learning the language i want",
        )
    }
}

@Composable
private fun InfoFieldDisplay(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.contains("YYYY") || value.contains("City")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailingContent?.invoke()
        }
    }
}

@Composable
private fun LearningProfileGradientSection(
    isEditing: Boolean,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    learningProfile: LearningProfile?,
    onUpdateField: (ProfileField, Any) -> Unit,
    onUpdateList: (ProfileField, List<String>) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                    )
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        ) {
            // Decorative blur circle
            Box(
                modifier =
                    Modifier
                        .size(384.dp)
                        .offset(x = (-100).dp, y = (-50).dp)
                        .background(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color(0x33A855F7), // purple-400/20
                                            Color(0x333B82F6), // blue-400/20
                                            Color.Transparent,
                                        ),
                                ),
                            shape = CircleShape,
                        ),
            )

            Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush =
                                            Brush.linearGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFFA855F7), // purple-400
                                                        Color(0xFF3B82F6), // blue-500
                                                    ),
                                            ),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "📚", style = MaterialTheme.typography.headlineMedium)
                        }

                        Text(
                            text = "Learning Profile",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (isEditing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onCancel) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = onSave,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier =
                                    Modifier.background(
                                        brush =
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFFA855F7), Color(0xFF3B82F6)),
                                            ),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    } else {
                        TextButton(onClick = onEdit) {
                            Text("✏️ Edit", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                learningProfile?.let { profile ->
                    if (isEditing) {
                        LearningProfileEditForm(
                            learningProfile = profile,
                            onUpdateField = onUpdateField,
                            onUpdateList = onUpdateList,
                        )
                    } else {
                        LearningProfileViewGrid(learningProfile = profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningProfileViewGrid(learningProfile: LearningProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LearningFieldCard(
                label = "Current Level",
                value = learningProfile.currentLevel,
                modifier = Modifier.weight(1f),
            )
            LearningFieldCard(
                label = "Primary Goal",
                value = learningProfile.primaryGoal,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LearningFieldCard(
                label = "Weekly Goal",
                value = "${learningProfile.weeklyGoalHours} hours",
                modifier = Modifier.weight(1f),
            )
            LearningFieldCard(
                label = "Learning Style",
                value = learningProfile.preferredLearningStyle,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LearningFieldCard(
                label = "Focus Areas",
                value = if (learningProfile.focusAreas.isNotEmpty()) learningProfile.focusAreas.joinToString(", ") else "Not set",
                modifier = Modifier.weight(1f),
            )
            LearningFieldCard(
                label = "Available Time Slots",
                value =
                    if (learningProfile.availableTimeSlots.isNotEmpty()) {
                        learningProfile.availableTimeSlots.joinToString(
                            ", ",
                        )
                    } else {
                        "Not set"
                    },
                modifier = Modifier.weight(1f),
            )
        }

        LearningFieldCard(
            label = "Motivations",
            value = if (learningProfile.motivations.isNotEmpty()) learningProfile.motivations.joinToString(", ") else "Not set",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LearningFieldCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PersonalInfoEditForm(
    personalInfo: PersonalInfo,
    onUpdateField: (ProfileField, String) -> Unit,
    onUpdateTargetLanguages: (List<String>) -> Unit,
    isEmailVerified: Boolean,
    onVerifyEmail: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = personalInfo.firstName,
                onValueChange = { onUpdateField(ProfileField.FIRST_NAME, it) },
                label = { Text("First Name *") },
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = personalInfo.lastName,
                onValueChange = { onUpdateField(ProfileField.LAST_NAME, it) },
                label = { Text("Last Name *") },
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = personalInfo.email,
            onValueChange = { onUpdateField(ProfileField.EMAIL, it) },
            label = { Text("Email *") },
            enabled = !isEmailVerified,
            trailingIcon = {
                if (isEmailVerified) {
                    Text("✅", style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(onClick = onVerifyEmail) {
                        Text("Verify", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = personalInfo.dateOfBirth ?: "",
                onValueChange = { onUpdateField(ProfileField.DATE_OF_BIRTH, it) },
                label = { Text("Date of Birth") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = personalInfo.location ?: "",
                onValueChange = { onUpdateField(ProfileField.LOCATION, it) },
                label = { Text("Location") },
                placeholder = { Text("City, Country") },
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = personalInfo.bio ?: "",
            onValueChange = { onUpdateField(ProfileField.BIO, it) },
            label = { Text("Bio") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LearningProfileEditForm(
    learningProfile: LearningProfile,
    onUpdateField: (ProfileField, Any) -> Unit,
    onUpdateList: (ProfileField, List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = learningProfile.currentLevel,
                onValueChange = { onUpdateField(ProfileField.CURRENT_LEVEL, it) },
                label = { Text("Current Level") },
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = learningProfile.primaryGoal,
                onValueChange = { onUpdateField(ProfileField.PRIMARY_GOAL, it) },
                label = { Text("Primary Goal") },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = learningProfile.weeklyGoalHours.toString(),
                onValueChange = {
                    val hours = it.toIntOrNull() ?: 0
                    onUpdateField(ProfileField.WEEKLY_GOAL_HOURS, hours)
                },
                label = { Text("Weekly Goal (Hours)") },
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = learningProfile.preferredLearningStyle,
                onValueChange = { onUpdateField(ProfileField.LEARNING_STYLE, it) },
                label = { Text("Learning Style") },
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = learningProfile.focusAreas.joinToString(", "),
            onValueChange = {
                val areas = it.split(",").map { area -> area.trim() }.filter { area -> area.isNotEmpty() }
                onUpdateList(ProfileField.FOCUS_AREAS, areas)
            },
            label = { Text("Focus Areas") },
            placeholder = { Text("Speaking, Grammar, Vocabulary") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = learningProfile.availableTimeSlots.joinToString(", "),
            onValueChange = {
                val slots = it.split(",").map { slot -> slot.trim() }.filter { slot -> slot.isNotEmpty() }
                onUpdateList(ProfileField.TIME_SLOTS, slots)
            },
            label = { Text("Available Time Slots") },
            placeholder = { Text("Morning, Evening, Weekend") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = learningProfile.motivations.joinToString(", "),
            onValueChange = {
                val motivations = it.split(",").map { motivation -> motivation.trim() }.filter { motivation -> motivation.isNotEmpty() }
                onUpdateList(ProfileField.MOTIVATIONS, motivations)
            },
            label = { Text("Motivations") },
            placeholder = { Text("Career Growth, Travel, Personal Interest") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
