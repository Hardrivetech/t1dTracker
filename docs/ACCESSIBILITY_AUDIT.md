Accessibility audit checklist

This checklist helps guide manual and automated accessibility testing.

- Verify all interactive elements have content descriptions or labels.
- Ensure focus order is logical and keyboard-navigation friendly.
- Test with TalkBack or other screen readers for correct spoken output.
- Check color contrast ratios meet WCAG AA minimums.
- Ensure touch target sizes are >= 48dp where appropriate.
- Provide alternative text for images and icons used in the UI.
- Validate dynamic content changes are announced to accessibility services.

Automated checks:
- Use `androidx.test.espresso:espresso-contrib` for accessibility matchers.
- Use `Accessibility Test Framework` for Android in CI when possible.

This is a scaffold; perform manual checks on target devices/emulators before release.
