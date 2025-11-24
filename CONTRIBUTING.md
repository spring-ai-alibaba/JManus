# How to Contribute

## Thank you for contributing to Lynxe!

Since Lynxe was open-sourced, it has received attention from many community members. Every Issue and PR from the community helps the project and contributes to building a better Lynxe.

We sincerely thank all developers who have contributed Issues and PRs to this project. We hope more community developers will join us to make the project even better.

## How to Contribute

Before contributing code, please take a moment to understand the process for contributing to Lynxe.

### What to Contribute?

We welcome any contributions at any time, whether it's a simple typo fix, bug fix, or adding new features. Please feel free to raise issues or submit PRs. We equally value documentation and integration with other open-source projects, and welcome contributions in these areas.

If it's a relatively complex modification, it's recommended to first add a Feature label in an Issue and briefly describe the design and modification points.

### Where to Start?

If you're a first-time contributor, you can start by claiming a relatively simple task from [good first issue](https://github.com/spring-ai-alibaba/Lynxe/labels/good%20first%20issue) and [help wanted](https://github.com/spring-ai-alibaba/Lynxe/labels/help%20wanted).

### Sign the Contributor License Agreement (CLA)

Before submitting a Pull Request, you need to sign our [Contributor License Agreement (CLA)](./CLA.md). This is to protect the rights of the project and all contributors and avoid potential copyright issues.

**How to Sign the CLA:**

1. When you submit a PR for the first time, the CLA bot will automatically check if you have signed the CLA
2. If you haven't signed it, the bot will leave a comment in the PR prompting you to sign
3. Please read the [CLA document](./CLA.md) carefully
4. Reply with the following text in the PR comment to sign the CLA:
   ```
   I have read the CLA Document and I hereby sign the CLA
   ```
5. After signing, the CLA bot will automatically update the PR status

**Important Notes:**
- Each contributor only needs to sign the CLA once
- If you're contributing code on behalf of a company or organization, please ensure you have the authority to sign the CLA on behalf of that organization
- After signing the CLA, all your future contributions will be protected by this agreement

### Fork the Repository and Clone it Locally

- Click the `Fork` button in the top right corner of [this project](https://github.com/spring-ai-alibaba/Lynxe) to fork spring-ai-alibaba/Lynxe to your own space.
- Clone the Lynxe repository under your account to your local machine. For example, if your account is `yourname`, execute `git clone https://github.com/yourname/Lynxe.git` to clone.

### Configure Github Information

- Execute `git config --list` on your machine to check the global username and email for git.
- Check if the displayed user.name and user.email match your GitHub username and email.
- If your company has its own GitLab or uses other commercial GitLab services, there may be a mismatch. In this case, you need to set the username and email separately for the Lynxe project.
- For how to set username and email, please refer to GitHub's official documentation: [Setting your username](https://help.github.com/articles/setting-your-username-in-git/#setting-your-git-username-for-a-single-repository), [Setting your email](https://help.github.com/articles/setting-your-commit-email-address-in-git/).

### Merge the Latest Code

After forking the code, the original repository's main branch may have new commits. To avoid conflicts between your PR and the commits in main, you need to merge the main branch in time.

- In your local Lynxe directory, execute `git remote add upstream https://github.com/spring-ai-alibaba/Lynxe` to add the original repository address to the remote stream.
- In your local Lynxe directory, execute `git fetch upstream` to fetch the remote stream locally.
- In your local Lynxe directory, execute `git checkout main` to switch to the main branch.
- In your local Lynxe directory, execute `git rebase upstream/main` to rebase the latest code.

### Configure Code Format

Before officially starting, please refer to the relevant code format specification instructions and configure the code format specifications before submitting code.

### Development

Develop your feature. **After development, it's recommended to use the `mvn clean package` command to ensure the modified code can compile locally. This command will also automatically format the code**. Then submit the code. Before submitting, please create a new branch related to this feature and use that branch for code submission.

### Local CI

After local development is complete, it's strongly recommended to execute the `make` commands provided by the project's `tools/make` for local Continuous Integration (CI) checks before submitting a PR to ensure the code meets project standards and specifications. If you have any questions about local CI, you can type `make help` in the console for more information.

### Local Checkstyle

To reduce unnecessary code style issues, Lynxe provides local Checkstyle checking functionality. You can execute the `mvn checkstyle:check` command in the project root directory to check if the code style conforms to specifications.

### Remove Unused Imports

To ensure code cleanliness, please remove unused imports from Java files. You can automatically remove unused imports by executing the `mvn spotless:apply` command.

### Commit Code

After coding is complete, format and check the commit message based on the PR specification in `.github/workflows/lint-pr-title.yml` to ensure the commit message conforms to specifications.

**Commit Specification:** `git commit -m "type(module): space compliant commit message"`

For example: `feat(docs): update contribution guide`

Common types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation update
- `style`: Code formatting adjustment
- `refactor`: Code refactoring
- `test`: Test related
- `chore`: Build/toolchain related

### Merge the Latest Code

- Similarly, before submitting a PR, you need to rebase the code from the main branch (if your target branch is not main, you need to rebase the corresponding target branch). For specific operation steps, please refer to the previous section.
- If conflicts occur, you need to resolve them first.

### Submit PR

Submit a PR, clearly state the modifications and implemented features according to the `Pull request template`, and wait for code review and merge.

**PR Submission Process:**

1. Push your branch to your forked repository
2. Create a Pull Request on GitHub
3. Fill in the PR template with a clear description of your changes
4. **Wait for the CLA bot to check, and sign the CLA if prompted**
5. Wait for CI checks to pass
6. Wait for project maintainers to conduct Code Review
7. Make modifications based on feedback (if needed)
8. After the PR is merged, you will become a Lynxe Contributor!

Thank you for contributing to Lynxe, let's build a better open-source project together!

## Code of Conduct

Please note that this project has a [Code of Conduct](./CODE_OF_CONDUCT.md), please follow it in all your interactions with the project.

## Issue Feedback

If you encounter any problems during the contribution process, feel free to:
- Ask questions in related Issues or PRs
- Check the project documentation and FAQ
- Contact project maintainers

## License

By contributing code to this project, you agree that your contributions will be licensed under the project's [LICENSE](./LICENSE).

