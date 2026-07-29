# دفتر حساب — نسخه اندروید (APK)

این پوشه اپ وب «دفتر حساب» را با [Capacitor](https://capacitorjs.com/) به اپ اندروید تبدیل می‌کند.

## ساخت APK

پیش‌نیاز: JDK 17+، Android SDK (platform 35، build-tools 35).

```bash
cd android-app
npm install
export ANDROID_HOME=/path/to/android-sdk
npx cap sync android
cd android && ./gradlew assembleDebug
```

خروجی دیباگ:

`android/app/build/outputs/apk/debug/app-debug.apk`

برای نصب روی گوشی:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## پایگاه داده

- **اصلی (داخل برنامه):** داده در حافظهٔ خصوصی اپ ذخیره می‌شود (localStorage + IndexedDB + فایل داخلی Capacitor در `Directory.Data`). نیازی به فایل جدا نیست.
- **اختیاری:** پشتیبان JSON / ZIP / ابری / فایل جدا (فقط مرورگر دسکتاپ)

هر تغییر بلافاصله داخل خود برنامه نوشته می‌شود.
