# Contributing to SchemFlow

We love contributions! This document explains how to contribute to SchemFlow.

## 🚀 Quick Start

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/c4g7-dev/SchemFlow.git`
3. **Create branch**: `git checkout -b feature/your-feature-name`
4. **Make changes** and test thoroughly
5. **Commit**: `git commit -m "Add your feature"`
6. **Push**: `git push origin feature/your-feature-name`
7. **Create Pull Request**

## 🛠️ Development Setup

### Prerequisites
- Java 17+ (OpenJDK recommended)
- Maven 3.6+
- Git
- IDE (IntelliJ IDEA recommended)

### Local Development
```bash
git clone https://github.com/c4g7-dev/SchemFlow.git
cd SchemFlow
mvn clean compile
```

### Testing
- Set up a Paper/Purpur test server (1.20.4+)
- Install WorldEdit and FastAsyncWorldEdit
- Copy built JAR to `plugins/` directory
- Configure test MinIO instance or S3 bucket

## 📝 Code Style

- Follow existing Java conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Keep methods focused and concise
- Use proper error handling

### Example Code Style
```java
/**
 * Uploads a schematic to S3 storage with compression.
 * 
 * @param file The schematic file to upload
 * @param objectName The target object name in storage
 * @throws Exception If upload fails
 */
public void uploadSchm(Path file, String objectName) throws Exception {
    // Implementation here
}
```

## 🐛 Bug Reports

When reporting bugs, please include:

- **SchemFlow version**
- **Server software** (Paper/Purpur version)
- **Java version**
- **WorldEdit version**
- **Steps to reproduce**
- **Expected vs actual behavior**
- **Error logs** (if any)

Use our [bug report template](https://github.com/c4g7-dev/SchemFlow/issues/new?template=bug_report.md).

## 💡 Feature Requests

For new features:

- Check existing issues first
- Explain the use case clearly
- Describe the proposed solution
- Consider implementation complexity
- Discuss alternatives

Use our [feature request template](https://github.com/c4g7-dev/SchemFlow/issues/new?template=feature_request.md).

## 🔍 Pull Request Guidelines

### Before Submitting
- [ ] Test your changes thoroughly
- [ ] Update documentation if needed
- [ ] Follow code style guidelines
- [ ] Write clear commit messages
- [ ] Rebase on latest main branch

### PR Description
- Explain what changes you made
- Reference related issues
- Include testing instructions
- Note any breaking changes

### Review Process
1. **Automated checks** must pass
2. **Code review** by maintainers
3. **Testing** in development environment
4. **Merge** when approved

## 🏷️ Commit Message Format

Use conventional commits:

```
type(scope): description

[optional body]

[optional footer]
```

### Types
- `feat`: New features
- `fix`: Bug fixes
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Test changes
- `chore`: Build/tool changes

### Examples
```
feat(commands): add bulk upload command
fix(s3): handle connection timeout gracefully
docs(readme): update installation instructions
```

## 🧪 Testing Guidelines

- Test on multiple server versions
- Verify FastAsyncWorldEdit compatibility
- Test large schematic operations
- Check memory usage patterns
- Validate error handling

## 📚 Documentation

- Update README.md for user-facing changes
- Add JavaDoc for new public APIs
- Update CHANGELOG.md
- Create wiki pages for complex features

## 🎯 Areas for Contribution

### High Priority
- Performance optimizations
- Additional S3 provider support
- Enhanced error handling
- Memory usage improvements

### Medium Priority
- UI/UX improvements
- Additional Skript integrations
- Configuration validation
- Backup/restore features

### Low Priority
- Code cleanup
- Documentation improvements
- Example scripts
- Testing enhancements

## 🤝 Community

- **Discord**: (optional) You can reach maintainers via GitHub Issues/Discussions

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to SchemFlow! 🌊