#!/bin/bash
echo "🚀 Preparing repository for Release..."

# Add all files (including GitHub workflows)
git add .
git commit -m "chore: configure release automation (GitHub Actions, F-Droid metadata)"

# Push master first
echo "📤 Pushing code to GitHub..."
git push origin master

# Create and push tag to trigger GitHub Action
echo "🏷️ Tagging version v1.1.0 to trigger release..."
# Force tag update if it exists locally but wasn't pushed effectively
git tag -f -a v1.1.0 -m "Release 1.1.0"
git push -f origin v1.1.0

echo "✅ Done!"
echo "➡️ Go to https://github.com/Dynag1/CardioLens/actions to see your APK being built."
echo "➡️ Once finished, the APK will be available in 'Releases' on the right sidebar."
