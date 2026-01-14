#!/bin/bash
echo "🚀 Preparing repository for F-Droid..."

# Add all files (including new license and metadata)
git add .
git add LICENSE
git add fastlane/

# Commit
git commit -m "chore: prepare for F-Droid release (add license, metadata, BYOK)"

# Tag version 1.1.0 (Critical for F-Droid build to find the right commit)
git tag -a v1.1.0 -m "Release 1.1.0 for F-Droid"

# Push to GitHub
echo "📤 Pushing code and tags to GitHub..."
git push origin master
git push origin v1.1.0

echo "✅ Code pushed successfully!"
echo "Now go to https://gitlab.com/fdroid/fdroiddata to submit your merge request."
