import { describeOutcome } from './status';

describe('describeOutcome', () => {
  it('names the outcome', () => {
    expect(describeOutcome('PASSED', 0)).toBe('Passed');
    expect(describeOutcome('ERROR', 0)).toBe('Error');
  });

  it('mentions failed attempts before the final outcome', () => {
    expect(describeOutcome('PASSED', 1)).toBe('Passed after 1 failed attempt');
    expect(describeOutcome('PASSED', 2)).toBe('Passed after 2 failed attempts');
  });
});
