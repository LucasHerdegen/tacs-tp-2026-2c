import React from 'react';

type BadgeVariant = 'success' | 'warning' | 'error' | 'info';

interface BadgeProps {
  variant: BadgeVariant;
  children: React.ReactNode;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({ variant, children, className = '' }) => {
  let colorClass = '';
  switch (variant) {
    case 'success':
      colorClass = 'badge-success';
      break;
    case 'warning':
      colorClass = 'badge-warning';
      break;
    case 'error':
      colorClass = 'bg-red-100 text-red-800'; // Or create badge-danger in CSS
      break;
    case 'info':
      colorClass = 'bg-indigo-100 text-indigo-800';
      break;
  }

  return (
    <span className={`badge ${colorClass} ${className}`}>
      {children}
    </span>
  );
};
