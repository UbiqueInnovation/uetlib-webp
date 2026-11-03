# Contributing

See the [shared uetlib contribution guide](https://github.com/UbiqueInnovation/uetlib/blob/main/CONTRIBUTING.md). Report ordinary issues in the [uetlib issue tracker](https://github.com/UbiqueInnovation/uetlib/issues), identifying this repository and version.

## Commit signing and DCO

Every contribution must use cryptographically signed commits and include a DCO sign-off. By adding your sign-off, you certify the [Developer Certificate of Origin 1.1](https://developercertificate.org/). Use your own name and an email address matching the commit author:

```sh
git commit -S -s -m "Describe the change"
```

`-S` signs the commit with your configured signing key; `-s` adds the `Signed-off-by` trailer. Configure [GitHub commit signature verification](https://docs.github.com/en/authentication/managing-commit-signature-verification) so your signature is verified. The [DCO GitHub App](https://github.com/apps/dco) checks PR commit sign-offs. Both checks are required on the default and release branches, including for organization members. Do not add a sign-off on another author's behalf.
