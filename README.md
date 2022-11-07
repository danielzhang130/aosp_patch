# aosp\_patch

Save changes to aosp `repo` managed git repositories as patches. This way, no need to download the full git history in order to make a push to a fork. Simply save the changes as a patch and apply it to any aosp revision you have locally (resolving conflicts as necessary).

## Commands

### Apply patch

```
aosp_patch apply
```

### Save changes as patch

```
aosp_patch format <project dir> <base git ref (excluding) of patch>
aosp_patch format frameworks/base HEAD~1
```

## Config file ~/.config/aosp\_patch.properties

```
aosp_dir=<path to aosp source tree root>
patch_dir=<path to folder, optionally git repo, to save patches>
```

