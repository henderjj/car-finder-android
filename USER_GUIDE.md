# Car Finder — User Guide

Car Finder remembers where you parked and helps you walk back to your car when you can't see it. It works automatically when your phone disconnects from your car over Bluetooth, but you can also save a location manually any time.

## First-time setup

Open the app for the first time and it will guide you through a few quick steps.

### 1. Location permission

Tap **Grant** and choose **While using the app** at the system prompt. Car Finder needs your location to remember where you parked and to show you the way back.

### 2. Background location

Tap **Grant** — Android opens a settings screen; choose **Allow all the time**. This is what lets Car Finder fetch the parking fix at the moment your car disconnects, when the app isn't on screen. Without it, automatic capture can't run (the manual **Save parking now** button still works). Don't worry: the app still only ever reads your location at the moment you park — see ["What Car Finder does NOT do"](#what-car-finder-does-not-do) below.

### 3. Notifications (optional but recommended)

Tap **Grant** to allow notifications. This is how the app tells you "parking saved" after an automatic capture. You can skip it — capture still works — but you won't see the confirmation, and you lose the one-tap shortcut to record your bay number.

### 4. Background running (battery optimisation)

Tap **Grant** and choose **Allow** at the system prompt. This exempts Car Finder from battery optimisation so Android allows it to wake up and save your spot when the car disconnects. Skipping this means automatic capture may silently never run on many phones.

### 5. Bluetooth permission

Tap **Grant Bluetooth permission**. The app uses this to see which paired device is your car and to detect when you disconnect from it.

### 6. Pick your car

You'll see a list of every Bluetooth device your phone is currently paired with. Tap the one that's your car. Devices that look like cars (hands-free / car-audio profiles) sort to the top with a "likely car" badge.

If your car isn't in the list, it isn't paired with your phone yet. Tap **Open Bluetooth settings**, pair your phone with the car as you would for hands-free calls or audio, then come back and tap **Refresh list**.

That's setup done. You won't see these screens again unless you change cars.

## Using the app day to day

You don't need to do anything while driving. The app stays silent until your car's Bluetooth disconnects.

### When you park

The moment your phone disconnects from the car's Bluetooth, Car Finder wakes up, fetches a GPS fix, and saves it. Within a few seconds a notification appears:

> **Parking saved**  
> Tap to view the location.  
> **\[ Add bay details ]**

You have two options:

- **Tap the body of the notification** — opens the app to the map view.
- **Tap "Add bay details"** — opens the app straight to the bay-entry screen so you can type your level/section/space while you're still standing next to the car.

### Finding your car

Open the app at any time and you'll see:

- **The map**, with a red pin where the car is parked and a blue dot for your current location.
- **The compass**, floating over the bottom of the map. The needle always points toward the car. The **N / E / S / W** letters rotate as you turn so they always indicate true compass directions — **N** is highlighted in red so you can pick out north at a glance.
- **The distance**, just below the map (e.g. "120 m to your car" — switches to km past 1 km).
- **The time** you parked, on the right of the same row.

Walk in the direction the needle points. The distance counts down as you get closer. When you're within a few metres of the car, GPS noise will make the needle wobble — that's normal. Just look up.

### Recording the bay / level / section

GPS gets you to the area but not to the exact bay, especially in multi-storey or large supermarket car parks. To pin it down:

1. Tap the **Add car park details** chip below the map. (If you've already added details, the same chip now shows them — tap it to edit.)
2. Type any combination of **Level**, **Section**, and **Space**. You don't have to fill in all three — leave the ones that don't apply blank.
3. Tap **Save**.

The chip on the main screen updates to show what you entered, e.g. "Level 3  ·  Section B  ·  Space 042". To change it later, tap the chip again. To remove everything, tap the chip and choose **Clear all** in the dialog.

**Tip:** Use the **Add bay details** action on the "Parking saved" notification to jump straight into this dialog without having to find the chip — fastest path while you're still next to the car.

### Manual save

Not every park comes with Bluetooth. If you walked, cycled, were dropped off, or borrowed a car you haven't paired, tap **Save parking now** on the main screen to capture your current location instead. Everything after that — map, compass, distance, bay details — works exactly the same.

### Other actions

- **Clear saved location** — appears once you have a saved parking. Removes the location and any bay details. Tap this once you're back at the car so the next save isn't compared against a stale spot.
- **Change paired car** — opens the device picker again. Use this if you've changed cars, are borrowing a different one, or your car's Bluetooth has re-paired with a new address.

## Troubleshooting

### The "Parking saved" notification never appears after I park

First check the two grants automatic capture depends on:

1. **Location must be "Allow all the time".** Settings → Apps → Car Finder → Permissions → Location → **Allow all the time**. If it's set to "While using the app", Android blocks the capture the moment the screen is off or you're in another app.
2. **Battery optimisation must be off for Car Finder.** If you skipped the "Background running" step during setup, Android may refuse to start the capture at all.

If both are set, the next most common cause is the manufacturer's own battery saver killing the app's background receiver before it can run — this sits on top of standard Android battery optimisation. Phones from Samsung, Xiaomi, Huawei, OnePlus, and Oppo are particularly aggressive about this. Add Car Finder to your phone's allowlist:

- **Samsung:** Settings → Apps → Car Finder → Battery → set to **Unrestricted**.
- **Xiaomi / Redmi / POCO:** Settings → Apps → Car Finder → Battery saver → **No restrictions**, then go to the same app's screen and turn on **Autostart**.
- **Huawei / Honor:** Settings → Apps → Car Finder → Battery → App launch → **Manage manually** → enable all three toggles.
- **OnePlus / Oppo / Realme:** Settings → Battery → Battery optimisation → Car Finder → **Don't optimise**.
- **Pixel / stock Android:** Settings → Apps → Car Finder → Battery → **Unrestricted**.

After changing the setting, drive somewhere and park again to verify. The manual **Save parking now** button is always available as a fallback.

### The saved location is in the wrong place

The first GPS fix immediately after a Bluetooth disconnect can be inaccurate, especially inside underground or multi-storey car parks where the phone can't see many satellites. If the saved spot is clearly wrong, use **Save parking now** once you're outside in the open — the new save replaces the old one. Letting the phone sit outside for a few seconds before saving usually gives a much better fix.

### The compass needle is jittery

If you're standing right next to the saved spot, small GPS errors translate to large changes in bearing because the actual distance to the car is so short — the needle will swing around. Walk a few metres in any direction and it'll settle.

The other compass enemy is metal — lifts, large parked vans, steel-framed buildings can all distort the magnetic field your phone uses. When the phone detects this its heading becomes unreliable, and the app shows a **⚠ Calibrate** prompt in the centre of the compass. Wave the phone through a figure-8 a few times, rotating your wrist as you go, until the prompt clears. Stepping outside or away from the metal helps it settle too.

### I re-paired my car and auto-save stopped working

Some cars (notably certain BMW and Mercedes head units) assign a new Bluetooth address when you re-pair. The app stores the address rather than the device name, so it no longer recognises the disconnect. Tap **Change paired car** and re-pick the car from the list.

### Map tiles look blurry or won't load

The map uses OpenStreetMap, which downloads tiles over the internet the first time you view a new area. Subsequent visits use the cached tiles. If the map is stuck loading, check you have a working data connection and re-open the app.

## What Car Finder does NOT do

- It doesn't track you in real time. Although Android's permission is called "Allow all the time", your location is only fetched at two moments: when you tap **Save parking now**, and when your car's Bluetooth disconnects. The "all the time" grant exists purely so the second one is allowed to happen while the app is in the background — there is no continuous tracking.
- It doesn't share or upload your location anywhere. Everything stays on your phone.
- It doesn't give you turn-by-turn navigation. It gives you a straight-line bearing and distance — for the last few metres in a complex car park, the bay/level/section details are how you bridge the gap.
