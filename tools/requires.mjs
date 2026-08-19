const REGISTER_LITERAL =
  /registerPlugin\s*(?:<[^>()]*>)?\s*\(\s*(["'`])([A-Za-z0-9_]+)\1/g;
const REGISTER_ANY = /registerPlugin\s*(?:<[^>()]*>)?\s*\(/g;
const DYNAMIC_ACCESS = /Capacitor\s*(?:\.|\[\s*["'])\s*Plugins/g;
// `registerPlugin` is not a name Capacitor owns — gsap exports one too, and matching on the call
// alone reports every gsap.registerPlugin(CSSPlugin) as an underivable Capacitor plugin.
const IMPORTS_REGISTER =
  /(?:import[\s\S]{0,200}?\bregisterPlugin\b[\s\S]{0,200}?from\s*|require\s*\(\s*)["'`]@capacitor\/core["'`]/;

/**
 * A name this cannot resolve makes the contract too small, and a contract too small is worse than
 * none: the device accepts a bundle it cannot run. Callers must treat either escape as fatal.
 *
 * `firstParty` scopes the dynamic-access check to code the app controls. Capacitor's own bridge
 * reaches plugins through `Capacitor.Plugins` by design, so flagging it everywhere fails every build.
 */
export function collectRequires(code, { firstParty = true } = {}) {
  const names = new Set();
  let unresolved = 0;

  if (IMPORTS_REGISTER.test(code)) {
    let literals = 0;
    for (const match of code.matchAll(REGISTER_LITERAL)) {
      names.add(match[2]);
      literals += 1;
    }
    unresolved = [...code.matchAll(REGISTER_ANY)].length - literals;
  }

  return {
    names,
    unresolved,
    dynamic: firstParty ? [...code.matchAll(DYNAMIC_ACCESS)].length : 0,
  };
}

export function describeContract(names) {
  return [...names].sort();
}
