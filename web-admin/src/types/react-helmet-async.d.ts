declare module 'react-helmet-async' {
  import * as React from 'react';

  export interface HelmetProviderProps {
    children?: React.ReactNode;
  }

  export const HelmetProvider: React.ComponentType<HelmetProviderProps>;
  export const Helmet: React.ComponentType<any>;
}

