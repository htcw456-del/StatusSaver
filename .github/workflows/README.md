# Status Saver

WhatsApp status (photos + videos) ko gallery mein save karne wali Android app.
Kotlin + Jetpack Compose, 3D animations, aur koi login nahi.

## Build karne ka tareeqa
1. Android Studio (Koala ya naya) install karein.
2. `File > Open` se is `StatusSaver` folder ko kholein, Gradle sync hone dein.
3. Phone USB se jorein (USB debugging on) aur Run dabayein.
   APK chahiye to: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

## Istemal
- Pehli dafa "Allow Access" dabayein, phir "Use this folder" — bas, dobara kabhi nahi poochhe ga.
- WhatsApp mein status dekhein, app mein refresh dabayein, download button dabayein.
- Save hui files `Pictures/StatusSaver` aur `Movies/StatusSaver` mein hoti hain.

## Note
App WhatsApp account se login nahi karti. Wo sirf us folder ko parhti hai jahan WhatsApp status
khud store karta hai, is liye "aik dafa sign in" ki jagah "aik dafa folder permission" hoti hai.
