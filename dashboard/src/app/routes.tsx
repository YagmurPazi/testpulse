import type { RouteObject } from 'react-router';
import { RepositoriesPage } from '../features/repositories/RepositoriesPage';
import { RepositoryPage } from '../features/repository/RepositoryPage';
import { TestPage } from '../features/test/TestPage';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <RepositoriesPage /> },
      { path: 'repositories/:owner/:name', element: <RepositoryPage /> },
      { path: 'tests/:testId', element: <TestPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
