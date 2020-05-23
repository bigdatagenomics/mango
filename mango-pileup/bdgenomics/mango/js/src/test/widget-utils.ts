// helper functions for widget tests

export function isValidUrl(string) {
  try {
    new URL(string);
  } catch (_) {
    return false;
  }

  return true;
}
