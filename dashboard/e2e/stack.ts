import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { expect, type APIRequestContext } from '@playwright/test';

const INGESTION_URL = process.env['INGESTION_URL'] ?? 'http://localhost:8081';
const DEMO_RUNS = path.resolve(import.meta.dirname, '../../demo/runs');

/** The demo's flaky story: an error and a pass on one commit, then a pass after a Surefire rerun. */
const FLAKY_STORY = ['02-checkout-attempt-1', '03-checkout-attempt-2', '04-checkout-next-commit'];

export const FLAKY_TEST = 'appliesDiscount';

/**
 * Uploads the flaky story under a repository name of its own, so every test starts from fresh data and
 * the tests neither depend on each other nor on the demo seed, and waits until the analysis is done.
 */
export async function seedFlakyRepository(request: APIRequestContext, label: string): Promise<string> {
  const repository = `e2e-${label}/checkout-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  for (const run of FLAKY_STORY) {
    await upload(request, repository, path.join(DEMO_RUNS, run));
  }
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/v1/repositories/${repository}/tests?status=FLAKY`);
        return response.ok()
          ? ((await response.json()) as { testName: string }[]).map((t) => t.testName)
          : [];
      },
      { message: `analysis marks ${FLAKY_TEST} in ${repository} as flaky` },
    )
    .toContain(FLAKY_TEST);
  return repository;
}

async function upload(request: APIRequestContext, repository: string, runDir: string): Promise<void> {
  const env = parseEnv(await readFile(path.join(runDir, 'run.env'), 'utf8'));
  const form = new FormData();
  form.set('repository', repository);
  form.set('commitSha', required(env, 'COMMIT_SHA'));
  form.set('branch', required(env, 'BRANCH'));
  form.set('externalRunId', required(env, 'EXTERNAL_RUN_ID'));
  form.set('attempt', required(env, 'ATTEMPT'));
  for (const file of (await readdir(runDir)).filter((name) => name.endsWith('.xml'))) {
    const xml = await readFile(path.join(runDir, file));
    form.append('reports', new Blob([xml], { type: 'application/xml' }), file);
  }
  const response = await request.post(`${INGESTION_URL}/api/v1/test-runs`, { multipart: form });
  expect(response.status(), `upload of ${path.basename(runDir)}`).toBe(201);
}

function parseEnv(content: string): Map<string, string> {
  const entries = content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line): [string, string] => {
      const separator = line.indexOf('=');
      return [line.slice(0, separator), line.slice(separator + 1)];
    });
  return new Map(entries);
}

function required(env: Map<string, string>, key: string): string {
  const value = env.get(key);
  if (value === undefined) {
    throw new Error(`run.env lacks ${key}`);
  }
  return value;
}

const MAILPIT_URL = process.env['MAILPIT_URL'] ?? 'http://localhost:8025';

/** Waits until notification-service's e-mail with this subject has arrived in Mailpit. */
export async function expectMail(request: APIRequestContext, subject: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT_URL}/api/v1/messages?limit=500`);
        const body = (await response.json()) as { messages: { Subject: string }[] };
        return body.messages.map((message) => message.Subject);
      },
      { message: `e-mail "${subject}"` },
    )
    .toContain(subject);
}
