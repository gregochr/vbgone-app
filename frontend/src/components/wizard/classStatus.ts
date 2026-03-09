export type ClassStatus = 'Pending' | 'In Progress' | 'Complete' | 'PR Raised'

/** Returns true when ALL dependencies of className have status Complete or PR Raised. */
export function isClassEnabled(
  className: string,
  dependencyGraph: Record<string, string[]>,
  statuses: Record<string, ClassStatus>,
): boolean {
  const deps = dependencyGraph[className] || []
  return deps.every((d) => statuses[d] === 'Complete' || statuses[d] === 'PR Raised')
}

/** Returns the first unmigrated dependency name, or null if all are migrated. */
export function firstUnmigratedDep(
  className: string,
  dependencyGraph: Record<string, string[]>,
  statuses: Record<string, ClassStatus>,
): string | null {
  const deps = dependencyGraph[className] || []
  return deps.find((d) => statuses[d] !== 'Complete' && statuses[d] !== 'PR Raised') || null
}
