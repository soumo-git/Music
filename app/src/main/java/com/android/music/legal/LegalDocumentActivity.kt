package com.android.music.legal

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.music.R
import com.android.music.databinding.ActivityLegalDocumentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LegalDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLegalDocumentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegalDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val documentType = intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: DOCUMENT_USER_AGREEMENT
        
        setupToolbar(documentType)
        displayDocument(documentType)
    }

    private fun setupToolbar(documentType: String) {
        val title = when (documentType) {
            DOCUMENT_USER_AGREEMENT -> getString(R.string.user_agreement)
            DOCUMENT_PRIVACY_POLICY -> getString(R.string.privacy_policy)
            else -> ""
        }
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun displayDocument(documentType: String) {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val lastUpdated = dateFormat.format(Date())
        
        binding.tvLastUpdated.text = "Last Updated: $lastUpdated"
        
        when (documentType) {
            DOCUMENT_USER_AGREEMENT -> {
                binding.tvDocumentTitle.text = "Terms of Service & User Agreement"
                binding.tvDocumentContent.text = formatUserAgreement()
            }
            DOCUMENT_PRIVACY_POLICY -> {
                binding.tvDocumentTitle.text = "Privacy Policy"
                binding.tvDocumentContent.text = formatPrivacyPolicy()
            }
        }
    }

    private fun formatUserAgreement(): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        val secondaryColor = ContextCompat.getColor(this, R.color.textSecondary)
        
        // Intro
        appendSection(builder, null, """
By installing, accessing, or using this application in any manner, you acknowledge that you have read, understood, and agreed to be bound by these terms. If you do not agree, please uninstall and cease all use of the application.
        """.trimIndent(), accentColor, secondaryColor)
        
        builder.append("\n\n")
        
        // Section 1
        appendSection(builder, "1. Acceptance of Terms", """
Use of the Application signifies full and unconditional acceptance of these Terms. No oral statements, assumptions, or informal communications shall supersede or modify this Agreement.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 2
        appendSection(builder, "2. Nature of the Application", """
The Application is a general-purpose media player and media management tool.

The Application:
• Does NOT host, store, stream, or distribute any copyrighted content
• Does NOT provide, recommend, or curate any media source
• Does NOT bypass or interfere with DRM or copyright protection systems

All media accessed through the Application originates from sources independently chosen by the User.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 3
        appendSection(builder, "3. User Responsibility", """
The User is solely responsible for:
• The origin, legality, and authorization of all content
• Ensuring compliance with copyright and intellectual property laws
• Any consequences arising from usage

The Developer shall not be construed as a publisher, distributor, or facilitator of content.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 4
        appendSection(builder, "4. Prohibited Uses", """
The Application shall NOT be used for:
• Copyright infringement
• Unauthorized redistribution of protected works
• Circumvention of licensing or access controls
• Any activity prohibited under applicable law

Misuse immediately terminates the User's right to access the Application.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 5
        appendSection(builder, "5. Third-Party Services", """
The Application may interact with Third-Party Services. The Developer:
• Has no control over such services
• Assumes no responsibility for their availability or content
• Makes no warranties regarding their legality or reliability

Disputes with Third-Party Services are strictly between the User and those parties.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 6
        appendSection(builder, "6. Local Connectivity Features", """
The Application may provide features enabling:
• Local device-to-device communication
• Offline synchronization
• Local streaming via Bluetooth, Wi-Fi Direct, or similar technologies

These features operate without centralized servers and transmit no data to the Developer.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 7
        appendSection(builder, "7. Disclaimer of Warranties", """
THE APPLICATION IS PROVIDED "AS IS" AND "AS AVAILABLE".

The Developer disclaims all warranties, including:
• MERCHANTABILITY
• FITNESS FOR A PARTICULAR PURPOSE
• NON-INFRINGEMENT
• ACCURACY OR RELIABILITY

No guarantee is made that the Application will function without interruption or error.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 8
        appendSection(builder, "8. Limitation of Liability", """
To the maximum extent permitted by law, the Developer shall not be liable for:
• Indirect, incidental, or consequential damages
• Loss of data, revenue, or profits
• Device damage or system failure
• Legal actions arising from User conduct

Use of the Application is at the User's sole risk.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 9
        appendSection(builder, "9. Indemnification", """
The User agrees to defend, indemnify, and hold harmless the Developer from any claims, liabilities, or expenses arising from:
• User misuse of the Application
• Violation of these Terms
• Infringement of third-party rights
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 10
        appendSection(builder, "10. Modifications", """
The Developer reserves the right to:
• Modify these Terms at any time
• Suspend or terminate access to the Application
• Discontinue features without prior notice

Continued use constitutes acceptance of all revisions.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 11
        appendSection(builder, "11. Governing Law", """
These Terms shall be governed by applicable laws. Any disputes shall be subject to the jurisdiction of competent courts.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Final
        builder.append("\n\n")
        val finalText = "By using this Application, you confirm that you have read, understood, and voluntarily accepted all terms herein."
        val start = builder.length
        builder.append(finalText)
        builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        return builder
    }

    private fun formatPrivacyPolicy(): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        val secondaryColor = ContextCompat.getColor(this, R.color.textSecondary)
        
        // Intro
        appendSection(builder, null, """
By installing, accessing, or using the Application, you acknowledge that you have read, understood, and agreed to this Privacy Policy.
        """.trimIndent(), accentColor, secondaryColor)
        
        builder.append("\n\n")
        
        // Core Principle
        val principleStart = builder.length
        builder.append("CORE PRINCIPLE\n")
        builder.setSpan(StyleSpan(Typeface.BOLD), principleStart, builder.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(accentColor), principleStart, builder.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append("Minimal Data. No Surveillance. No Centralized Tracking.\n\n")
        
        // Section 1
        appendSection(builder, "1. Information We Do NOT Collect", """
The Application does NOT collect, store, transmit, or sell:
• Personal identifiers (name, phone number, address)
• Contact lists
• Messages or communications
• Browsing history
• Media consumption history
• Audio, video, or microphone recordings
• Device files unrelated to user-selected media
• Biometric or sensitive personal data
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 2
        appendSection(builder, "2. Local Data Storage", """
Certain data may be stored locally on your device only:
• App preferences and settings
• Playlist metadata created by you
• Playback state and UI configuration
• Equalizer presets and audio preferences

This data:
• Never leaves your device
• Is not transmitted to the Developer
• Can be deleted by uninstalling the app or clearing app data
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 3
        appendSection(builder, "3. Third-Party Services", """
The Application may optionally interact with Third-Party Services chosen by you.

The Developer:
• Does NOT control Third-Party Services
• Does NOT receive data processed by them
• Is NOT responsible for their privacy practices

You are responsible for reviewing Third-Party privacy policies.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 4
        appendSection(builder, "4. Local Connectivity", """
The Application may enable local device-to-device communication using:
• Bluetooth
• Wi-Fi Direct
• Local network protocols

All such communication:
• Occurs directly between your devices
• Does NOT pass through Developer servers
• Is NOT monitored or logged by the Developer
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 5
        appendSection(builder, "5. Permissions", """
The Application requests only permissions necessary for functionality:
• Media access (to play files you select)
• Audio output control
• Bluetooth or local connectivity (optional)

Permissions are:
• Never abused
• Never used for background surveillance
• Fully revocable via system settings
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 6
        appendSection(builder, "6. No Analytics, No Ads, No Trackers", """
The Application:
• Does NOT embed advertising SDKs
• Does NOT use analytics or tracking frameworks
• Does NOT fingerprint devices
• Does NOT perform behavioral profiling
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 7
        appendSection(builder, "7. Data Security", """
Because no personal data is collected or transmitted, the risk of data breaches is inherently minimized.

The Developer implements reasonable safeguards for local data integrity but makes no guarantees against device-level compromise.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 8
        appendSection(builder, "8. Children's Privacy", """
The Application does not knowingly collect data from minors. Responsibility for ensuring lawful use by minors rests with parents or guardians.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 9
        appendSection(builder, "9. Policy Modifications", """
The Developer reserves the right to modify this Privacy Policy at any time. Continued use after changes constitutes acceptance of the revised Policy.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Section 10
        appendSection(builder, "10. Governing Law", """
This Privacy Policy shall be governed by applicable laws, without regard to conflict-of-law principles.
        """.trimIndent(), accentColor, secondaryColor)
        
        // Final Statement
        builder.append("\n\n")
        val finalStart = builder.length
        builder.append("FINAL STATEMENT\n\n")
        builder.setSpan(StyleSpan(Typeface.BOLD), finalStart, builder.length - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(accentColor), finalStart, builder.length - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        val statementStart = builder.length
        builder.append("The Application does not want your data.\nThe Developer does not profit from your data.\nYour data remains yours — on your device — under your control.")
        builder.setSpan(StyleSpan(Typeface.BOLD), statementStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        return builder
    }

    private fun appendSection(
        builder: SpannableStringBuilder,
        title: String?,
        content: String,
        accentColor: Int,
        secondaryColor: Int
    ) {
        if (title != null) {
            builder.append("\n")
            val titleStart = builder.length
            builder.append(title)
            builder.append("\n\n")
            builder.setSpan(StyleSpan(Typeface.BOLD), titleStart, titleStart + title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(accentColor), titleStart, titleStart + title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(RelativeSizeSpan(1.1f), titleStart, titleStart + title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        builder.append(content)
        builder.append("\n")
    }

    companion object {
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val DOCUMENT_USER_AGREEMENT = "user_agreement"
        const val DOCUMENT_PRIVACY_POLICY = "privacy_policy"

        fun startUserAgreement(context: Context) {
            val intent = Intent(context, LegalDocumentActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_TYPE, DOCUMENT_USER_AGREEMENT)
            }
            context.startActivity(intent)
        }

        fun startPrivacyPolicy(context: Context) {
            val intent = Intent(context, LegalDocumentActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_TYPE, DOCUMENT_PRIVACY_POLICY)
            }
            context.startActivity(intent)
        }
    }
}
