# Shield Browser (ShieldBrowse)

Brave-এর মতো প্রাইভেসি-প্রথম, বিজ্ঞাপনমুক্ত Android ব্রাউজার — Firefox-এর GeckoView ইঞ্জিনে তৈরি।

## বৈশিষ্ট্য (v1.0.0)

- **GeckoView (Firefox) ইঞ্জিন** — গুগল WebView নয়
- **আসল Extension সাপোর্ট** — Firefox Add-ons (AMO) থেকে uBlock Origin, Dark Reader, Privacy Badger ইত্যাদি
- **অ্যাড ও ট্র্যাকার ব্লকিং** — Enhanced Tracking Protection (Strict চেষ্টা) + প্রতি-ট্যাব ট্র্যাকিং প্রোটেকশন
- **Tabs + Private Tab** — প্রাইভেট ট্যাবের হিস্টোরি কখনো সেভ হয় না
- **Bookmark ও History** — শুধু ফোনে থাকে, কোনো সার্ভারে যায় না, কোনো অ্যাকাউন্ট নেই
- **Default সার্চ DuckDuckGo** (Google/Bing-এ বদলানো যায়)
- Classic Glass ডিজাইন, বাংলা UI

## সীমাবদ্ধতা (সৎভাবে)

- Chrome Web Store-এর এক্সটেনশন চলে না — শুধু Firefox Add-ons।
- APK বড় হবে — GeckoView ইঞ্জিনই ~২০০ MB AAR; এটি হালকা WebView অ্যাপ নয়।
- ডাউনলোড ম্যানেজার ও Find-in-page v1.0.0-তে নেই।

## ইনস্টল

> ⚠️ **আগে আনইনস্টল, তারপর ইনস্টল** — CI ডিবাগ বিল্ডের সাইনিং প্রতিবার আলাদা হয়, তাই পুরোনো সংস্করণের উপরে ইনস্টল বসে না। আনইনস্টল করলে লোকাল বুকমার্ক/হিস্টোরি মুছে যাবে। ডাউনলোড WiFi-তে করুন — APK বড়।
