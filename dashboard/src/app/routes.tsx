import type { RouteObject } from 'react-router';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <p>Dashboard</p> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
