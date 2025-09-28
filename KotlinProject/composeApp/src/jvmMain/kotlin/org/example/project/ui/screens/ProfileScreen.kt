package org.example.project.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.presentation.viewmodel.ProfileViewModel
import org.example.project.ui.components.*
import org.example.project.ui.theme.WordBridgeColors
import org.example.project.domain.model.*
import org.example.project.core.auth.User as AuthUser
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.net.URI


@Composable
fun ProfileScreen(
    authenticatedUser: AuthUser? = null,
    viewModel: ProfileViewModel = viewModel(),
    modifier: Modifier = Modifier
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
            println("🔄 ProfileScreen - Initializing profile for user: ${authUser.email}")
            viewModel.initializeWithAuthenticatedUser(authUser)
        }
    }
    
    LaunchedEffect(userProfile, isLoading) {
        if (userProfile.personalInfo.firstName.isNotEmpty()) {
            println("✅ ProfileScreen - Profile loaded successfully: ${userProfile.personalInfo.fullName}")
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary
            )
            
            if (isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = WordBridgeColors.PrimaryPurple
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        

        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WordBridgeColors.PrimaryPurple)
            }
        } else {
            ProfileHeader(
                profile = userProfile,
                completion = profileCompletion,
                isEditingPicture = viewModel.isEditingProfilePicture.value,
                tempImageBytes = viewModel.tempProfileImageBytes.value,
                onStartEditPhoto = viewModel::onStartProfilePictureEdit,
                onSavePhoto = viewModel::onSaveProfilePicture,
                onCancelEditPhoto = viewModel::onCancelProfilePictureEdit,
                isSaving = isSaving
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (profileCompletion.completionPercentage < 100) {
                ProfileCompletionCard(
                    completion = profileCompletion
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            ProfileSection(
                title = "Personal Information",
                icon = "👤",
                isEditing = editingSection == ProfileSection.PERSONAL_INFO,
                onEdit = { viewModel.onStartSectionEdit(ProfileSection.PERSONAL_INFO) },
                onSave = viewModel::onSaveSectionChanges,
                onCancel = viewModel::onCancelSectionEdit
            ) {
                if (editingSection == ProfileSection.PERSONAL_INFO && editingPersonalInfo != null) {
                    PersonalInfoEditForm(
                        personalInfo = editingPersonalInfo!!,
                        onUpdateField = viewModel::onUpdatePersonalInfoField,
                        onUpdateTargetLanguages = viewModel::onUpdateTargetLanguages,
                        isEmailVerified = userProfile.accountInfo.isEmailVerified,
                        onVerifyEmail = viewModel::onVerifyEmail
                    )
                } else {
                    PersonalInfoViewMode(
                        personalInfo = userProfile.personalInfo,
                        isEmailVerified = userProfile.accountInfo.isEmailVerified,
                        onVerifyEmail = viewModel::onVerifyEmail
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection(
                title = "Learning Profile",
                icon = "📚",
                isEditing = editingSection == ProfileSection.LEARNING_PROFILE,
                onEdit = { viewModel.onStartSectionEdit(ProfileSection.LEARNING_PROFILE) },
                onSave = viewModel::onSaveSectionChanges,
                onCancel = viewModel::onCancelSectionEdit
            ) {
                if (editingSection == ProfileSection.LEARNING_PROFILE && editingLearningProfile != null) {
                    LearningProfileEditForm(
                        learningProfile = editingLearningProfile!!,
                        onUpdateField = viewModel::onUpdateLearningProfileField,
                        onUpdateList = viewModel::onUpdateLearningProfileList
                    )
                } else {
                    LearningProfileViewMode(
                        learningProfile = userProfile.learningProfile
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection(
                title = "Account & Security",
                icon = "🔒"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AccountInfoItem(
                        label = "Subscription",
                        value = userProfile.accountInfo.subscriptionType,
                        status = userProfile.accountInfo.subscriptionStatus,
                        onClick = viewModel::onManageSubscription
                    )
                    
                    SecurityToggleItem(
                        label = "Two-Factor Authentication",
                        description = "Add an extra layer of security",
                        isEnabled = userProfile.accountInfo.twoFactorEnabled,
                        onToggle = viewModel::onToggleTwoFactor
                    )
                    
                    SecurityActionItem(
                        label = "Change Password",
                        description = "Update your account password",
                        onClick = viewModel::onChangePassword
                    )
                    
                    if (!userProfile.accountInfo.isPhoneVerified) {
                        SecurityActionItem(
                            label = "Verify Phone Number",
                            description = "Secure your account with phone verification",
                            onClick = viewModel::onVerifyPhone
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection(
                title = "Your Statistics",
                icon = "📊"
            ) {
                ProfileStatsGrid(stats = userProfile.profileStats)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection(
                title = "Profile Customization",
                icon = "🎨"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileCustomizationItem(
                        label = "App Theme",
                        currentValue = "Light Mode",
                        options = listOf("Light Mode", "Dark Mode", "System Default"),
                        onValueChange = {  }
                    )
                    
                    ProfileCustomizationItem(
                        label = "Display Language",
                        currentValue = "English",
                        options = listOf("English", "Spanish", "French", "German"),
                        onValueChange = {  }
                    )
                    
                    ProfileCustomizationItem(
                        label = "Notifications",
                        currentValue = "All Enabled",
                        options = listOf("All Enabled", "Learning Only", "Disabled"),
                        onValueChange = {  }
                    )
                    
                    ProfileCustomizationItem(
                        label = "Learning Reminders",
                        currentValue = "Daily at 7 PM",
                        options = listOf("Daily at 7 PM", "Daily at 9 AM", "Every 2 days", "Disabled"),
                        onValueChange = {  }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection(
                title = "Account Actions",
                icon = "⚙️"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DangerActionItem(
                        label = "Export My Data",
                        description = "Download all your learning data",
                        onClick = viewModel::onExportData
                    )
                    
                    DangerActionItem(
                        label = "Delete Account",
                        description = "Permanently delete your account and all data",
                        onClick = viewModel::onRequestAccountDeletion,
                        isDangerous = true
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


@Composable
private fun ProfileHeader(
    profile: UserProfile,
    completion: ProfileCompletion,
    isEditingPicture: Boolean = false,
    tempImageBytes: ByteArray? = null,
    onStartEditPhoto: () -> Unit = {},
    onSavePhoto: () -> Unit = {},
    onCancelEditPhoto: () -> Unit = {},
    isSaving: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.BackgroundWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(
                            if (!isEditingPicture) WordBridgeColors.PrimaryPurple.copy(alpha = 0.3f) 
                            else Color.Blue.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .clickable { 
                            if (!isEditingPicture) {
                                onStartEditPhoto()
                                println("🖱️ Avatar clicked - starting photo edit")
                            }
                        }
                )
                
                Card(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (!isEditingPicture) {
                                onStartEditPhoto()
                                println("🖱️ Avatar clicked - starting photo edit")
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = WordBridgeColors.PrimaryPurple),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    if (isEditingPicture && tempImageBytes != null) {
                        Image(
                            bitmap = Image.makeFromEncoded(tempImageBytes).asImageBitmap(),
                            contentDescription = "New Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        UserAvatar(
                            initials = profile.personalInfo.initials,
                            profileImageUrl = profile.personalInfo.profileImageUrl,
                            size = 100.dp,
                            onClick = {
                                if (!isEditingPicture) {
                                    onStartEditPhoto()
                                }
                            }
                        )
                    }
                }
                
                    
                if (!isEditingPicture) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .offset(x = 30.dp, y = 30.dp)
                            .background(WordBridgeColors.PrimaryPurple, CircleShape)
                            .clickable { 
                                onStartEditPhoto()
                                println("🖱️ Camera icon clicked - starting photo edit") 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (profile.personalInfo.profileImageUrl.isNullOrEmpty()) "📷" else "✏️",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (profile.personalInfo.fullName.isBlank()) {
                    if (profile.personalInfo.firstName.isNotBlank() || profile.personalInfo.lastName.isNotBlank()) {
                        "${profile.personalInfo.firstName} ${profile.personalInfo.lastName}".trim()
                    } else {
                        "Complete your profile"
                    }
                } else {
                    profile.personalInfo.fullName
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = WordBridgeColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = profile.personalInfo.email.ifBlank { "No email set" },
                style = MaterialTheme.typography.bodyMedium,
                color = WordBridgeColors.TextSecondary
            )
            
            // Show save/cancel buttons when editing profile picture
            if (isEditingPicture) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancelEditPhoto,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = WordBridgeColors.TextSecondary
                        ),
                        elevation = null
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onSavePhoto,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WordBridgeColors.PrimaryPurple
                        ),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...", color = Color.White)
                            }
                        } else {
                            Text("Save Picture", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ProfileCompletionCard(
    completion: ProfileCompletion,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)) // Light purple
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile Completion",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = WordBridgeColors.TextPrimary
                )
                
                Text(
                    text = "${completion.completionPercentage}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WordBridgeColors.PrimaryPurple
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { completion.completionPercentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = WordBridgeColors.PrimaryPurple,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}


@Composable
private fun ProfileSection(
    title: String,
    icon: String,
    isEditing: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WordBridgeColors.BackgroundWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                }
                
                if (isEditing) {
                    Row {
                        TextButton(onClick = onCancel ?: {}) {
                            Text(
                                text = "Cancel",
                                color = WordBridgeColors.TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = onSave ?: {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WordBridgeColors.PrimaryPurple
                            )
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                } else if (onEdit != null) {
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = WordBridgeColors.PrimaryPurple
                        ),
                        elevation = null
                    ) {
                        Text("Edit")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            content()
        }
    }
}


@Composable
private fun ProfileFieldItem(
    label: String,
    value: String,
    placeholder: String? = null,
    isRequired: Boolean = false,
    isVerified: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onVerify: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    if (isRequired) {
                        Text(
                            text = " *",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFEF4444) // Red
                        )
                    }
                    
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✅",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (value.isEmpty()) (placeholder ?: "Not set") else value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isEmpty()) WordBridgeColors.TextMuted 
                           else WordBridgeColors.TextPrimary
                )
            }
            
            Row {
                if (onVerify != null && !isVerified) {
                    TextButton(onClick = onVerify) {
                        Text(
                            text = "Verify",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.PrimaryPurple
                        )
                    }
                }
                
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.bodySmall,
                            color = WordBridgeColors.PrimaryPurple
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun AccountInfoItem(label: String, value: String, status: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(text = "$value ($status)", style = MaterialTheme.typography.bodySmall, color = WordBridgeColors.TextSecondary)
        }
    }
}

@Composable private fun SecurityToggleItem(label: String, description: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = WordBridgeColors.TextSecondary)
        }
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}

@Composable private fun SecurityActionItem(label: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = WordBridgeColors.TextSecondary)
        }
    }
}

@Composable private fun ProfileStatsGrid(stats: ProfileStats) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("Study Time", "${stats.totalStudyTime / 60}h", modifier = Modifier.weight(1f))
            StatItem("Lessons", "${stats.lessonsCompleted}", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("Words Learned", "${stats.wordsLearned}", modifier = Modifier.weight(1f))
            StatItem("Achievements", "${stats.achievementsUnlocked}", modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = WordBridgeColors.PrimaryPurple)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = WordBridgeColors.TextSecondary)
    }
}

@Composable private fun DangerActionItem(label: String, description: String, onClick: () -> Unit, isDangerous: Boolean = false) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (isDangerous) Color(0xFFEF4444) else WordBridgeColors.TextPrimary)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = WordBridgeColors.TextSecondary)
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return "Jan 2024"
}


@Composable
private fun ProfileCustomizationItem(
    label: String,
    currentValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WordBridgeColors.TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = WordBridgeColors.TextSecondary
                    )
                }
                
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = WordBridgeColors.TextSecondary
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                options.forEach { option ->
                    Card(
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (option == currentValue) 
                                WordBridgeColors.PrimaryPurple.copy(alpha = 0.1f)
                            else 
                                Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == currentValue) 
                                WordBridgeColors.PrimaryPurple 
                            else 
                                WordBridgeColors.TextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PersonalInfoViewMode(
    personalInfo: PersonalInfo,
    isEmailVerified: Boolean,
    onVerifyEmail: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileFieldItem(
            label = "First Name",
            value = personalInfo.firstName,
            isRequired = true
        )
        
        ProfileFieldItem(
            label = "Last Name",
            value = personalInfo.lastName,
            isRequired = true
        )
        
        ProfileFieldItem(
            label = "Email",
            value = personalInfo.email,
            isRequired = true,
            isVerified = isEmailVerified,
            onVerify = if (!isEmailVerified) onVerifyEmail else null
        )
        
        ProfileFieldItem(
            label = "Date of Birth",
            value = personalInfo.dateOfBirth ?: "",
            placeholder = "YYYY-MM-DD"
        )
        
        ProfileFieldItem(
            label = "Location",
            value = personalInfo.location ?: "",
            placeholder = "City, Country"
        )
        
        ProfileFieldItem(
            label = "Native Language",
            value = personalInfo.nativeLanguage,
            isRequired = true
        )
        
        ProfileFieldItem(
            label = "Target Languages",
            value = if (personalInfo.targetLanguages.isNotEmpty()) 
                personalInfo.targetLanguages.joinToString(", ") 
            else "",
            placeholder = "English, Spanish, French"
        )
        
        ProfileFieldItem(
            label = "Bio",
            value = personalInfo.bio ?: "",
            placeholder = "Share something about your language learning journey"
        )
    }
}


@Composable
private fun PersonalInfoEditForm(
    personalInfo: PersonalInfo,
    onUpdateField: (ProfileField, String) -> Unit,
    onUpdateTargetLanguages: (List<String>) -> Unit,
    isEmailVerified: Boolean,
    onVerifyEmail: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = personalInfo.firstName,
            onValueChange = { onUpdateField(ProfileField.FIRST_NAME, it) },
            label = { Text("First Name *") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = personalInfo.lastName,
            onValueChange = { onUpdateField(ProfileField.LAST_NAME, it) },
            label = { Text("Last Name *") },
            modifier = Modifier.fillMaxWidth()
        )
        
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
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = personalInfo.dateOfBirth ?: "",
            onValueChange = { onUpdateField(ProfileField.DATE_OF_BIRTH, it) },
            label = { Text("Date of Birth") },
            placeholder = { Text("YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = personalInfo.location ?: "",
            onValueChange = { onUpdateField(ProfileField.LOCATION, it) },
            label = { Text("Location") },
            placeholder = { Text("City, Country") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = personalInfo.nativeLanguage,
            onValueChange = { onUpdateField(ProfileField.NATIVE_LANGUAGE, it) },
            label = { Text("Native Language *") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = personalInfo.targetLanguages.joinToString(", "),
            onValueChange = { 
                val languages = it.split(",").map { lang -> lang.trim() }.filter { lang -> lang.isNotEmpty() }
                onUpdateTargetLanguages(languages)
            },
            label = { Text("Target Languages") },
            placeholder = { Text("English, Spanish, French") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = personalInfo.bio ?: "",
            onValueChange = { onUpdateField(ProfileField.BIO, it) },
            label = { Text("Bio") },
            placeholder = { Text("Share something about your language learning journey") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun LearningProfileViewMode(
    learningProfile: LearningProfile
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileFieldItem(
            label = "Current Level",
            value = learningProfile.currentLevel
        )
        
        ProfileFieldItem(
            label = "Primary Goal",
            value = learningProfile.primaryGoal
        )
        
        ProfileFieldItem(
            label = "Weekly Goal",
            value = "${learningProfile.weeklyGoalHours} hours"
        )
        
        ProfileFieldItem(
            label = "Learning Style",
            value = learningProfile.preferredLearningStyle
        )
        
        ProfileFieldItem(
            label = "Focus Areas",
            value = if (learningProfile.focusAreas.isNotEmpty()) 
                learningProfile.focusAreas.joinToString(", ") 
            else "",
            placeholder = "Speaking, Grammar, Vocabulary"
        )
        
        ProfileFieldItem(
            label = "Available Time Slots",
            value = if (learningProfile.availableTimeSlots.isNotEmpty()) 
                learningProfile.availableTimeSlots.joinToString(", ") 
            else "",
            placeholder = "Morning, Evening, Weekend"
        )
        
        ProfileFieldItem(
            label = "Motivations",
            value = if (learningProfile.motivations.isNotEmpty()) 
                learningProfile.motivations.joinToString(", ") 
            else "",
            placeholder = "Career Growth, Travel, Personal Interest"
        )
    }
}


@Composable
private fun LearningProfileEditForm(
    learningProfile: LearningProfile,
    onUpdateField: (ProfileField, Any) -> Unit,
    onUpdateList: (ProfileField, List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = learningProfile.currentLevel,
            onValueChange = { onUpdateField(ProfileField.CURRENT_LEVEL, it) },
            label = { Text("Current Level") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = learningProfile.primaryGoal,
            onValueChange = { onUpdateField(ProfileField.PRIMARY_GOAL, it) },
            label = { Text("Primary Goal") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = learningProfile.weeklyGoalHours.toString(),
            onValueChange = { 
                val hours = it.toIntOrNull() ?: 0
                onUpdateField(ProfileField.WEEKLY_GOAL_HOURS, hours)
            },
            label = { Text("Weekly Goal (Hours)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = learningProfile.preferredLearningStyle,
            onValueChange = { onUpdateField(ProfileField.LEARNING_STYLE, it) },
            label = { Text("Learning Style") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = learningProfile.focusAreas.joinToString(", "),
            onValueChange = { 
                val areas = it.split(",").map { area -> area.trim() }.filter { area -> area.isNotEmpty() }
                onUpdateList(ProfileField.FOCUS_AREAS, areas)
            },
            label = { Text("Focus Areas") },
            placeholder = { Text("Speaking, Grammar, Vocabulary") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = learningProfile.availableTimeSlots.joinToString(", "),
            onValueChange = { 
                val slots = it.split(",").map { slot -> slot.trim() }.filter { slot -> slot.isNotEmpty() }
                onUpdateList(ProfileField.TIME_SLOTS, slots)
            },
            label = { Text("Available Time Slots") },
            placeholder = { Text("Morning, Evening, Weekend") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = learningProfile.motivations.joinToString(", "),
            onValueChange = { 
                val motivations = it.split(",").map { motivation -> motivation.trim() }.filter { motivation -> motivation.isNotEmpty() }
                onUpdateList(ProfileField.MOTIVATIONS, motivations)
            },
            label = { Text("Motivations") },
            placeholder = { Text("Career Growth, Travel, Personal Interest") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun AvatarContent(
    profile: UserProfile,
    isEditingPicture: Boolean,
    tempImageBytes: ByteArray?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isEditingPicture && tempImageBytes != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Blue.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "🖼️",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.Black
                        )
                        Text(
                            text = "NEW IMAGE\nSELECTED",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = 10.sp
                        )
                    }
                }
            }
            !profile.personalInfo.profileImageUrl.isNullOrEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Green.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .clickable {
                            try {
                                Desktop.getDesktop().browse(URI(profile.personalInfo.profileImageUrl))
                            } catch (e: Exception) {
                                println("❌ Failed to open image in browser: ${e.message}")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "📸",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.Black
                        )
                        Text(
                            text = "IMAGE\nUPLOADED",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = 10.sp
                        )
                        Text(
                            text = "CLICK TO VIEW",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            fontSize = 8.sp
                        )
                    }
                }
            }
            profile.personalInfo.avatar.isNotEmpty() && profile.personalInfo.avatar.length <= 2 -> {
                Text(
                    text = profile.personalInfo.avatar,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
            else -> {
                Text(
                    text = profile.personalInfo.initials,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
    }
    
    if (!isEditingPicture) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.3f),
                    CircleShape
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "📷",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}
