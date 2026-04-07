# Fort-android

Fort Wallet for Android.

## Build posture

This repository is self-contained for normal Android development and local app builds.
The app loads its bundled web payload from committed files under app/src/main/assets/payload/.
You do not need a sibling Fort-ios checkout just to clone this repo, open it in Android Studio, and run the app.

## Shared payload refresh

The current shared wallet UI and Pyodide payload originate from the active shared web lane in the sibling Fort-ios workspace repo.
That dependency exists only for refreshing the bundled payload snapshot, not for running the Android app.

If you are working in the full shared workspace, refresh the Android-bundled payload with:

```sh
./sync-payload.sh
```

That script rebuilds the shared payload in the sibling Fort-ios repo, copies the dist output into this repo's assets directory, and syncs the generated Kotlin bridge contract.

If Fort-ios is not present, the checked-in payload snapshot in this repo remains the source used by Android builds.
