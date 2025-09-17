# 🚀 SchemFlow Repository Setup Guide

This guide will help you set up SchemFlow as its own independent GitHub repository while keeping the original Skyd.Flow repository intact.

## 📋 Prerequisites

- Git installed and configured
- GitHub account with appropriate permissions
- Access to create repositories under Skydinse organization (or your organization)

## 🏗️ Step-by-Step Setup

### 1. Exclude SchemFlow from Skyd.Flow Repository

The `.gitignore` file has already been updated in the main Skyd.Flow repository to exclude the `SchemFlow/` directory:

```bash
# In the main Skyd.Flow repository
git add .gitignore
git commit -m "Exclude SchemFlow directory from Skyd.Flow repository"
git push origin main
```

### 2. Create New GitHub Repository

1. **Go to GitHub** and navigate to the Skydinse organization
2. **Click "New Repository"**
3. **Repository Settings:**
   - Name: `SchemFlow`
   - Description: `⚡ Lightning-fast schematic management powered by S3/MinIO storage`
   - Visibility: `Public`
   - Initialize: **Don't** initialize with README, .gitignore, or license (we have our own)

### 3. Initialize SchemFlow Repository Locally

```bash
# Navigate to the SchemFlow directory
cd "v:\GITHUB-REPOS\Skyd.repos\new\Skyd.Flow\SchemFlow"

# Initialize as git repository
git init

# Add all files
git add .

# Initial commit
git commit -m "🎉 Initial commit: SchemFlow v0.1.0

- Complete schematic management system with S3/MinIO storage
- FastAsyncWorldEdit support for maximum performance
- Comprehensive Skript integration
- Advanced world provisioning system
- Modern command interface with tab completion
- Extensive documentation and examples"

# Add remote origin (replace with your actual repository URL)
git remote add origin https://github.com/Skydinse/SchemFlow.git

# Push to GitHub
git branch -M main
git push -u origin main
```

### 4. Configure Repository Settings

After pushing, configure these settings on GitHub:

#### **General Settings**
- ✅ Enable "Issues"
- ✅ Enable "Wiki" 
- ✅ Enable "Discussions"
- ✅ Enable "Projects"
- ❌ Disable "Allow merge commits" (use squash and rebase only)

#### **Branch Protection Rules**
Create protection rule for `main` branch:
- ✅ Require pull request reviews before merging
- ✅ Require status checks to pass before merging
- ✅ Require up-to-date branches before merging
- ✅ Include administrators

#### **Topics/Tags**
Add these topics to help discoverability:
```
minecraft plugin java s3 minio worldedit skript schematics bukkit paper purpur
```

### 5. Set Up Release Automation

The GitHub Actions workflow is already configured. Create your first release:

```bash
# Create and push a tag for the first release
git tag -a v0.1.0 -m "SchemFlow v0.1.0 - Initial Release

🌟 Features:
- S3/MinIO schematic storage
- FastAsyncWorldEdit support  
- Skript integration
- World provisioning
- Advanced command system
- Comprehensive documentation

🚀 This is the initial open-source release of SchemFlow!"

git push origin v0.1.0
```

This will automatically trigger the release workflow and create a GitHub release with the built JAR file.

### 6. Create Additional Repository Content

#### **Wiki Pages** (Create these on GitHub Wiki):
- Installation Guide
- Configuration Reference  
- Command Documentation
- Skript API Reference
- Troubleshooting Guide
- Performance Tuning
- S3 Provider Setup Guides

#### **GitHub Discussions Categories:**
- 💡 Ideas & Feature Requests
- 🙋 Q&A & Support
- 📢 Announcements
- 🎮 Show and Tell
- 💬 General Discussion

### 7. Set Up Continuous Integration

The CI/CD pipeline is already configured with:

- ✅ **Build Automation**: Builds on every push/PR
- ✅ **Security Scanning**: CodeQL analysis
- ✅ **Release Automation**: Auto-releases on version tags
- ✅ **Artifact Storage**: Build artifacts stored for 30 days

### 8. Configure Webhooks (Optional)

For Discord notifications:
1. Create Discord webhook in your server
2. Add webhook URL to repository secrets as `DISCORD_WEBHOOK`
3. Update GitHub Actions to post notifications

## 📊 Verification Checklist

After setup, verify everything works:

- [ ] Repository is public and accessible
- [ ] README displays correctly with badges
- [ ] GitHub Actions build successfully
- [ ] Issues templates work
- [ ] Wiki is accessible
- [ ] Discussions are enabled
- [ ] Release workflow triggers on tags
- [ ] Branch protection is active

## 🔗 Important URLs

After setup, these URLs will be active:

- **Repository**: `https://github.com/Skydinse/SchemFlow`
- **Releases**: `https://github.com/Skydinse/SchemFlow/releases`
- **Issues**: `https://github.com/Skydinse/SchemFlow/issues`
- **Wiki**: `https://github.com/Skydinse/SchemFlow/wiki`
- **Discussions**: `https://github.com/Skydinse/SchemFlow/discussions`

## 🚨 Important Notes

1. **Keep Original Repository Clean**: The Skyd.Flow repository will not include SchemFlow directory after committing the `.gitignore` changes.

2. **Independent Development**: SchemFlow can now be developed completely independently from Skyd.Flow.

3. **Syncing Updates**: If you want to sync features from Skyd.Flow to SchemFlow in the future, you'll need to manually port changes.

4. **License Compliance**: Both repositories maintain proper attribution and licensing.

## 🎯 Next Steps

1. **Community Building**: Announce SchemFlow in Minecraft development communities
2. **Documentation**: Expand wiki with detailed guides
3. **Testing**: Set up beta testing program
4. **Feedback**: Gather community input for future features
5. **Marketing**: Create showcase videos and tutorials

---

🌊 **SchemFlow is now ready to be an independent, thriving open-source project!**