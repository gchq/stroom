export function onlyUnique<VALUE>(
  value: VALUE,
  index: number,
  self: VALUE[],
): boolean {
  return (
    self.map((m) => JSON.stringify(m)).indexOf(JSON.stringify(value)) === index
  );
}
