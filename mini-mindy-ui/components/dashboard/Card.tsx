"use client";

import { ReactNode } from "react";

interface CardProps {
  title: string;
  children: ReactNode;
  className?: string;
  icon?: ReactNode;
  gradient?: string;
}

export default function Card({ title, children, className, icon, gradient }: CardProps) {
  return (
    <div className={`relative bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden transition-all duration-300 hover:shadow-xl hover:border-gray-200 hover:-translate-y-1 ${className}`}>
      {/* Gradient decoration */}
      <div className={`absolute top-0 right-0 w-32 h-32 rounded-full blur-3xl opacity-50 -translate-y-1/2 translate-x-1/2 ${gradient || 'bg-gradient-to-br from-primary/20 to-purple-500/20'}`} />
      
      {/* Header */}
      <div className="relative px-6 pt-6 pb-4 border-b border-gray-50">
        <div className="flex items-center gap-3">
          {icon && (
            <div className="p-2.5 rounded-xl bg-gradient-to-br from-primary/10 to-purple-500/10 text-primary">
              {icon}
            </div>
          )}
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
        </div>
      </div>
      
      {/* Content */}
      <div className="relative p-6">
        {children}
      </div>
    </div>
  );
}
