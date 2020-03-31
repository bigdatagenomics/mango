# Releases

To cut a new release:

- Update `version` in `package.json`. Commit this change.
- Run `scripts/publish.sh`.
- Run `npm publish`.
- Push to github.

If you are publishing a beta version, run:

- `npm publish --tag beta`
