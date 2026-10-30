"use client"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import react from "react";

const QueryProvider = ({ children }: { children: React.ReactNode }) => {
    const [query, setQuery] = react.useState(() => new QueryClient());
    return <QueryClientProvider client={query}>{children}</QueryClientProvider>;
}

export default QueryProvider;
