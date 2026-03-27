# BookingHelper

BookingHelper is an Android utility application designed to streamline the process of filling repetitive booking forms (like travel, sports, or event registrations) across different apps. It provides a secure, offline-first way to store your personal details and quickly auto-fill them using an Accessibility Service and a floating overlay button.

## 🚀 Features

- **Local Storage (Offline-First)**: All your personal data is stored locally on your device using a Room database. No internet permission is required, ensuring your data remains private.
- **Floating Overlay Button**: A draggable, always-on-top floating button allows you to trigger the auto-fill process from any app without switching back to BookingHelper.
- **Smart Auto-Fill (Accessibility Service)**: Uses Android's Accessibility Service to scan form fields and match them with your saved data based on hints, placeholders, and IDs.
- **Comprehensive Data Support**:
    - **Personal**: Full Name, Age, Gender, Identity Type (Aadhar, PAN, etc.), Identity Number.
    - **Address & Extra**: Email, City, State, Country, Pincode, Gothram.

## 🛠 Tech Stack

- **Kotlin**: Primary programming language.
- **Android Gradle Plugin (AGP) 8.9.1**: Stable build configuration.
- **Room Database**: For secure local data persistence.
- **Accessibility Service**: For cross-app form field interaction.
- **System Alert Window (Overlay)**: For the floating UI component.
- **Coroutines & Flow**: For reactive and asynchronous data handling.

## 📋 How to Use

1.  **Save Your Details**: Open BookingHelper and fill in the personal and address information you frequently use in booking forms. Click **Save Details**.
2.  **Grant Permissions**:
    *   **Accessibility Service**: Enable "BookingHelper" in your device's Accessibility settings. This is required to read and fill form fields.
    *   **Display Over Other Apps**: Grant the overlay permission so the floating button can appear on top of other applications.
3.  **Start the Overlay**: Tap the **Start Overlay** button in the app. A floating edit icon will appear.
4.  **Auto-Fill in Action**:
    *   Open any booking app or website in your browser.
    *   Navigate to the registration or booking form.
    *   Tap the **Floating Button**.
    *   BookingHelper will automatically scan for fields like "Name", "Email", "Age", etc., and fill them with your saved data.

## 🔒 Privacy & Security

BookingHelper is designed with privacy as a core principle:
- **Zero Internet Access**: The app does not request internet permissions, meaning your data never leaves your device.
- **Local Persistence**: Data is stored in an encrypted/private Room database accessible only to the app.
- **Transparency**: You have full control over when the Accessibility Service is active and when the overlay is visible.

## 🏗 Development & Build

This project is built using:
- **Gradle Wrapper**: 8.x
- **Compile/Target SDK**: 36
- **Min SDK**: 24

To build the project locally, use:
```bash
./gradlew assembleDebug
```
